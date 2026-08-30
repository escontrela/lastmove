package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.arena.LichessBotAccount;
import com.escontrela.lastmove.application.arena.LichessBotClient;
import com.escontrela.lastmove.application.computer.*;
import com.escontrela.lastmove.application.game.*;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.domain.common.*;
import com.escontrela.lastmove.domain.game.*;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Drives Knightshade only when a Lichess game stream confirms that it is the bot's turn. */
@Component
public final class LichessArenaTurnService {
  private static final Logger log = LoggerFactory.getLogger(LichessArenaTurnService.class);
  private static final Duration DEFAULT_LIMIT = Duration.ofSeconds(5);
  private final ChessGameFactory games;
  private final ComputerMoveEngineProvider knightshade;
  private final SavedGameRepository savedGames; private final PlayerRepository players;
  private final Map<String, Runtime> runtimes = new ConcurrentHashMap<>();

  public LichessArenaTurnService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers, SavedGameRepository savedGames, PlayerRepository players) {
    this.games = Objects.requireNonNull(games);
    this.knightshade = providers.stream().filter(p -> ComputerEngineIds.KNIGHTSHADE.equals(p.descriptor().id())).findFirst()
        .orElseThrow(() -> new IllegalStateException("Knightshade engine provider is not available"));
    this.savedGames=Objects.requireNonNull(savedGames); this.players=Objects.requireNonNull(players);
  }

  public void consume(String gameId, JsonNode event, Optional<GameId> existingLocalGameId, Optional<String> tournamentId,
      LichessBotAccount account, String token, LichessBotClient client) {
    String type = event.path("type").asText();
    if ("gameFull".equals(type)) {
      JsonNode white = event.path("white"), black = event.path("black");
      PieceColor color = matches(white, account) ? PieceColor.WHITE : matches(black, account) ? PieceColor.BLACK : null;
      if (color == null) throw new IllegalStateException("The configured bot is not a player in this Lichess game");
      Runtime replacement = new Runtime(event.path("initialFen").asText("startpos"), color,
          playerName(white,"White"), playerName(black,"Black"), playerId(white), playerId(black),
          timeControl(event.path("clock")), tournamentId);
      replacement.localGameId = existingLocalGameId.orElse(null);
      Runtime old = runtimes.put(gameId, replacement);
      if (old != null) old.close();
    }
    Runtime runtime = runtimes.get(gameId);
    if (runtime == null || (!"gameFull".equals(type) && !"gameState".equals(type))) return;
    JsonNode state = "gameFull".equals(type) ? event.path("state") : event;
    runtime.moves = state.path("moves").asText(); runtime.state = state;
    persist(runtime, account);
    log.info("Lichess game state reconciled: gameId={} localGameId={} type={} status={} plies={}", gameId, runtime.localGameId, type, state.path("status").asText(), plyCount(runtime.moves));
    request(gameId, runtime, token, client);
  }

  public void close(String gameId) { Optional.ofNullable(runtimes.remove(gameId)).ifPresent(Runtime::close); }
  public Optional<GameId> localGameId(String gameId) { return Optional.ofNullable(runtimes.get(gameId)).map(r -> r.localGameId); }

  private void request(String gameId, Runtime runtime, String token, LichessBotClient client) {
    if (!"started".equals(runtime.state.path("status").asText())) return;
    ChessGame game = replay(runtime.initialFen, runtime.moves, runtime);
    if (game.currentTurn() != runtime.color || !runtime.thinking.compareAndSet(false, true)) return;
    String expectedMoves = runtime.moves;
    ComputerMoveEngine engine = knightshade.create(); runtime.engine = engine;
    engine.start().thenCompose(ignored -> engine.chooseMove(new ComputerMoveRequest(game.currentPosition(), limit(runtime))))
        .whenComplete((move, failure) -> {
          try { if (failure == null && expectedMoves.equals(runtime.moves)) client.sendMove(token, gameId, uci(move)); }
          finally { engine.close(); runtime.engine = null; runtime.thinking.set(false); if (!expectedMoves.equals(runtime.moves)) request(gameId, runtime, token, client); }
        });
  }

  private ChessGame replay(String initialFen, String moves, Runtime runtime) {
    ChessGame game = "startpos".equals(initialFen) ? games.createInitial(new GamePlayer(runtime.whiteName,PieceColor.WHITE),new GamePlayer(runtime.blackName,PieceColor.BLACK),Optional.of(runtime.timeControl)) : games.createFrom(Fen.of(initialFen),new GamePlayer(runtime.whiteName,PieceColor.WHITE),new GamePlayer(runtime.blackName,PieceColor.BLACK),Optional.of(runtime.timeControl));
    if (!moves.isBlank()) for (String uci : moves.split("\\s+")) if (!game.move(command(uci)).accepted()) throw new IllegalStateException("Could not replay Lichess move " + uci);
    if(runtime.localGameId==null)runtime.localGameId=game.id();
    GameRecord record=game.toRecord();
    return games.resume(runtime.localGameId,record.initialPosition(),record.currentPosition(),record.moves().stream().map(RecordedPly::ply).toList(),record.moves().stream().map(RecordedPly::clockBeforeMove).toList(),remoteClock(runtime),record.whitePlayer(),record.blackPlayer(),record.timeControl(),record.result(),record.terminationReason());
  }
  private void persist(Runtime runtime, LichessBotAccount account) { ChessGame game=replay(runtime.initialFen,runtime.moves,runtime);String winner=runtime.state.path("winner").asText(),status=runtime.state.path("status").asText();if(game.result().isEmpty()&&!winner.isBlank()&&("resign".equals(status)||"outoftime".equals(status)))game.resign("white".equalsIgnoreCase(winner)?PieceColor.BLACK:PieceColor.WHITE);Player bot=players.findByExternalIdentity("LICHESS",account.id()).orElseGet(()->players.save(Player.knightshadeBot(account.id())));List<PlayerId> participants=new ArrayList<>();participants.add(bot.id());participant(runtime.whiteLichessId,runtime.whiteName).ifPresent(participants::add);participant(runtime.blackLichessId,runtime.blackName).ifPresent(participants::add);runtime.localGameId=game.id();GameType type=runtime.tournamentId.isPresent()?GameType.LICHESS_BOT_TOURNAMENT:GameType.HUMAN_VS_COMPUTER;Optional<ComputerGameConfiguration> configuration=type==GameType.HUMAN_VS_COMPUTER?Optional.of(new ComputerGameConfiguration(account.username(),runtime.color,runtime.timeControl,Optional.empty(),ComputerEngineIds.KNIGHTSHADE,DEFAULT_LIMIT)):Optional.empty();savedGames.save(game,new SavedGameContext(type,Optional.of(bot.id()),configuration,participants));}
  private static Duration limit(Runtime runtime) {
    long remaining = runtime.color == PieceColor.WHITE ? runtime.state.path("wtime").asLong() : runtime.state.path("btime").asLong();
    return remaining <= 0 ? DEFAULT_LIMIT : Duration.ofMillis(Math.max(100, Math.min(DEFAULT_LIMIT.toMillis(), remaining - 500)));
  }
  private static MoveCommand command(String uci) {
    if (uci == null || !uci.matches("[a-h][1-8][a-h][1-8][qrbn]?")) throw new IllegalArgumentException("Invalid UCI move: " + uci);
    return new MoveCommand(Square.of(uci.substring(0, 2)), Square.of(uci.substring(2, 4)), uci.length() == 5 ? Optional.of(piece(uci.charAt(4))) : Optional.empty());
  }
  private static PieceType piece(char c) { return switch(c) { case 'q' -> PieceType.QUEEN; case 'r' -> PieceType.ROOK; case 'b' -> PieceType.BISHOP; case 'n' -> PieceType.KNIGHT; default -> throw new IllegalArgumentException("Invalid promotion"); }; }
  private static String uci(MoveCommand move) { return move.from().toAlgebraic() + move.to().toAlgebraic() + move.promotion().map(p -> switch(p) { case QUEEN -> "q"; case ROOK -> "r"; case BISHOP -> "b"; case KNIGHT -> "n"; default -> throw new IllegalStateException("Invalid promotion"); }).orElse(""); }
  private static boolean matches(JsonNode player, LichessBotAccount account) {
    JsonNode identity = player.has("user") ? player.path("user") : player;
    return account.id().equalsIgnoreCase(identity.path("id").asText())
        || account.username().equalsIgnoreCase(identity.path("name").asText())
        || account.username().equalsIgnoreCase(identity.path("username").asText());
  }
  private Optional<PlayerId> participant(Optional<String> id,String name){return id.map(accountId->players.findByExternalIdentity("LICHESS",accountId).orElseGet(()->players.save(Player.lichessAccount(accountId,name))).id());}
  private static Optional<String> playerId(JsonNode player){JsonNode identity=player.has("user")?player.path("user"):player;String id=identity.path("id").asText();return id.isBlank()?Optional.empty():Optional.of(id);}
  private static String playerName(JsonNode player,String fallback){JsonNode identity=player.has("user")?player.path("user"):player;String name=identity.path("name").asText();if(name.isBlank())name=identity.path("username").asText();if(name.isBlank())name=identity.path("id").asText();if(name.isBlank()&&player.has("aiLevel"))name="Stockfish level "+player.path("aiLevel").asInt();return name.isBlank()?fallback:name;}
  private static TimeControl timeControl(JsonNode clock){return clock.hasNonNull("initial")?TimeControl.of(Duration.ofMillis(clock.path("initial").asLong()),Duration.ofMillis(clock.path("increment").asLong())):TimeControl.unlimited();}
  private static GameClockSnapshot remoteClock(Runtime runtime){if(!runtime.timeControl.initialTime().isPresent())return GameClockSnapshot.initial(Optional.of(runtime.timeControl));return new GameClockSnapshot(Optional.of(Duration.ofMillis(Math.max(0,runtime.state.path("wtime").asLong()))),Optional.of(Duration.ofMillis(Math.max(0,runtime.state.path("btime").asLong()))));}
  private static int plyCount(String moves){return moves==null||moves.isBlank()?0:moves.trim().split("\\s+").length;}
  private static final class Runtime { final String initialFen; final PieceColor color; final String whiteName,blackName; final Optional<String> whiteLichessId,blackLichessId,tournamentId; final TimeControl timeControl; final AtomicBoolean thinking = new AtomicBoolean(); volatile String moves = ""; volatile JsonNode state; volatile ComputerMoveEngine engine; volatile GameId localGameId; Runtime(String initialFen, PieceColor color,String whiteName,String blackName,Optional<String> whiteLichessId,Optional<String> blackLichessId,TimeControl timeControl,Optional<String> tournamentId) { this.initialFen=initialFen; this.color=color;this.whiteName=whiteName;this.blackName=blackName;this.whiteLichessId=whiteLichessId;this.blackLichessId=blackLichessId;this.timeControl=timeControl;this.tournamentId=tournamentId; } void close() { if(engine != null) { engine.cancelSearch(); engine.close(); } } }
}
