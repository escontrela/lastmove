package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.game.GameType;
import com.escontrela.lastmove.application.game.SavedGame;
import com.escontrela.lastmove.application.game.SavedGameContext;
import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.training.memory.MemoryGamePosition;
import com.escontrela.lastmove.application.training.memory.MemoryGamePositionRepository;
import com.escontrela.lastmove.application.notification.GameNotificationRepository;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.GameClockSnapshot;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GamePlayer;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameTerminationReason;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.RecordedPly;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.game.InMemoryProgressiveGameRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Normalized SQLite store for every persisted chess game; mode data stays in side tables. */
@Repository
public class SqliteGameRepository implements SavedGameRepository, MemoryGamePositionRepository {
  private final JdbcTemplate jdbc;
  private final PersistenceAvailability availability;
  private final ChessGameFactory gameFactory;
  private final com.escontrela.lastmove.domain.game.ChessRulesEngine rulesEngine;
  private final FenService fenService;
  private final GameNotificationRepository notifications;
  private final InMemoryProgressiveGameRepository fallback = new InMemoryProgressiveGameRepository();

  public SqliteGameRepository(JdbcTemplate jdbc, PersistenceAvailability availability, ChessGameFactory gameFactory,
      com.escontrela.lastmove.domain.game.ChessRulesEngine rulesEngine, FenService fenService, GameNotificationRepository notifications) {
    this.jdbc = Objects.requireNonNull(jdbc); this.availability = Objects.requireNonNull(availability);
    this.gameFactory = Objects.requireNonNull(gameFactory); this.rulesEngine = Objects.requireNonNull(rulesEngine);
    this.fenService = Objects.requireNonNull(fenService);
    this.notifications = Objects.requireNonNull(notifications);
  }

  @Override @Transactional public void save(ChessGame game, SavedGameContext context) {
    if (!availability.isAvailable()) { fallback.save(game, context); return; }
    Objects.requireNonNull(game); Objects.requireNonNull(context);
    String id = game.id().value().toString(); var record = game.toRecord(); long now = Instant.now().toEpochMilli();
    String initialFen = fenService.fromSnapshot(record.initialPosition()).getValue();
    String currentFen = fenService.fromSnapshot(record.currentPosition()).getValue();
    GamePlayer white = record.whitePlayer().orElseThrow(); GamePlayer black = record.blackPlayer().orElseThrow();
    int exists = jdbc.queryForObject("SELECT COUNT(*) FROM games WHERE id=?", Integer.class, id);
    String previousStatus = exists == 0 ? null : jdbc.queryForObject("SELECT status FROM games WHERE id=?", String.class, id);
    Object[] values = {context.ownerPlayerId().map(PlayerId::value).orElse(null), context.gameType().name(),
      record.result().isPresent() ? "FINISHED" : "IN_PROGRESS", initialFen, currentFen, white.getName(), white.getElo().orElse(null),
      black.getName(), black.getElo().orElse(null), millis(game.currentClock().whiteRemaining()), millis(game.currentClock().blackRemaining()),
      record.timeControl().flatMap(TimeControl::initialTime).map(Duration::toMillis).orElse(null), record.timeControl().map(TimeControl::increment).map(Duration::toMillis).orElse(0L),
      record.result().map(Enum::name).orElse(null), record.terminationReason().map(Enum::name).orElse(null)};
    if (exists == 0) {
      Object[] insert = java.util.Arrays.copyOf(values, 18);
      insert[15] = now; insert[16] = now; insert[17] = id;
      jdbc.update("INSERT INTO games(owner_player_id,game_type,status,initial_fen,current_fen,white_name,white_elo,black_name,black_elo,white_remaining_ms,black_remaining_ms,time_control_initial_ms,time_control_increment_ms,result,termination_reason,created_at,updated_at,id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", insert);
    } else {
      Object[] update = java.util.Arrays.copyOf(values, 17);
      update[15] = now; update[16] = id;
      jdbc.update("UPDATE games SET owner_player_id=?,game_type=?,status=?,initial_fen=?,current_fen=?,white_name=?,white_elo=?,black_name=?,black_elo=?,white_remaining_ms=?,black_remaining_ms=?,time_control_initial_ms=?,time_control_increment_ms=?,result=?,termination_reason=?,updated_at=? WHERE id=?", update);
    }
    if (exists == 0) context.ownerPlayerId().ifPresent(owner -> notifications.notify(owner, game.id(), "GAME_CREATED"));
    if ("IN_PROGRESS".equals(previousStatus) && record.result().isPresent()) {
      context.ownerPlayerId().ifPresent(owner -> notifications.notify(owner, game.id(), "GAME_FINISHED"));
    }
    saveNewMoves(id, record.moves());
    saveParticipants(id, context);
    context.computerConfiguration().ifPresent(configuration -> saveComputerConfiguration(id, configuration));
  }

  private void saveParticipants(String gameId, SavedGameContext context) {
    List<PlayerId> participants = new ArrayList<>(context.participantPlayerIds());
    // Local Knightshade games have a second persisted identity even though the
    // legacy HVC context only carried the human owner.
    if (context.computerConfiguration().map(c -> com.escontrela.lastmove.application.computer.ComputerEngineIds.KNIGHTSHADE.equals(c.engineId())).orElse(false)) {
      jdbc.queryForList("SELECT id FROM players WHERE player_type='SYSTEM' AND external_provider='LICHESS'")
          .forEach(row -> participants.add(PlayerId.of(((Number) row.get("id")).longValue())));
    }
    List<PlayerId> uniqueParticipants = participants.stream().distinct().toList();
    for (PlayerId participant : uniqueParticipants) {
      jdbc.update("INSERT OR IGNORE INTO game_participants(game_id, player_id, participant_role, joined_at) VALUES(?,?,?,?)",
          gameId, participant.value(), "PLAYER", Instant.now().toEpochMilli());
    }
  }

  private void saveNewMoves(String id, List<RecordedPly> moves) {
    Integer last = jdbc.queryForObject("SELECT MAX(ply_index) FROM game_moves WHERE game_id=?", Integer.class, id);
    int existing = last == null ? 0 : last + 1;
    jdbc.update("DELETE FROM game_moves WHERE game_id=? AND ply_index>=?", id, moves.size());
    for (int i = existing; i < moves.size(); i++) {
      RecordedPly recorded = moves.get(i); Ply ply = recorded.ply(); MoveDescriptor move = ply.move();
      jdbc.update("INSERT INTO game_moves(game_id,ply_index,ply_id,san,move_from,move_to,promotion,capture,castle,en_passant,moving_color,move_number,resulting_fen,clock_before_white_ms,clock_before_black_ms) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,i,ply.id().toString(),move.san().getValue(),move.from().toAlgebraic(),move.to().toAlgebraic(),move.promotion().map(Enum::name).orElse(null),move.capture()?1:0,move.castling()?1:0,move.enPassant()?1:0,ply.movingColor().name(),ply.moveNumber(),fenService.fromSnapshot(ply.resultingPosition()).getValue(),millis(recorded.clockBeforeMove().whiteRemaining()),millis(recorded.clockBeforeMove().blackRemaining()));
    }
  }
  private void saveComputerConfiguration(String id, ComputerGameConfiguration c) {
    jdbc.update("INSERT INTO computer_game_configuration(game_id,human_name,human_color,engine_id,engine_thinking_ms) VALUES(?,?,?,?,?) ON CONFLICT(game_id) DO UPDATE SET human_name=excluded.human_name,human_color=excluded.human_color,engine_id=excluded.engine_id,engine_thinking_ms=excluded.engine_thinking_ms", id,c.humanName(),c.humanColor().name(),c.engineId(),c.engineThinkingTime().toMillis());
  }
  @Override @Transactional(readOnly = true) public Optional<SavedGame> findSaved(GameId gameId) {
    if (!availability.isAvailable()) return fallback.findSaved(gameId);
    return jdbc.queryForList("SELECT * FROM games WHERE id=?", gameId.value().toString()).stream().findFirst().map(this::hydrate);
  }
  private SavedGame hydrate(java.util.Map<String,Object> row) {
    String id=(String)row.get("id"); List<Ply> plies=new ArrayList<>(); List<GameClockSnapshot> before=new ArrayList<>();
    PositionSnapshot previousPosition = rulesEngine.positionFrom(Fen.of((String) row.get("initial_fen")));
    for (java.util.Map<String, Object> move : jdbc.queryForList("SELECT * FROM game_moves WHERE game_id=? ORDER BY ply_index", id)) {
      Ply ply = toPly(move, previousPosition);
      plies.add(ply);
      before.add(clock(move.get("clock_before_white_ms"), move.get("clock_before_black_ms")));
      previousPosition = ply.resultingPosition();
    }
    Optional<TimeControl> control=Optional.of(new TimeControl(optionalDuration(row.get("time_control_initial_ms")), Duration.ofMillis(((Number)row.get("time_control_increment_ms")).longValue())));
    ChessGame game=gameFactory.resume(new GameId(UUID.fromString(id)),rulesEngine.positionFrom(Fen.of((String)row.get("initial_fen"))),rulesEngine.positionFrom(Fen.of((String)row.get("current_fen"))),plies,before,clock(row.get("white_remaining_ms"),row.get("black_remaining_ms")),Optional.of(new GamePlayer((String)row.get("white_name"),PieceColor.WHITE,(Integer)row.get("white_elo"))),Optional.of(new GamePlayer((String)row.get("black_name"),PieceColor.BLACK,(Integer)row.get("black_elo"))),control,optionalEnum(GameResult.class,row.get("result")),optionalEnum(GameTerminationReason.class,row.get("termination_reason")));
    Optional<PlayerId> owner=Optional.ofNullable((Number)row.get("owner_player_id")).map(n->PlayerId.of(n.longValue())); GameType type=GameType.valueOf((String)row.get("game_type"));
    List<PlayerId> participants = jdbc.queryForList("SELECT player_id FROM game_participants WHERE game_id=? ORDER BY joined_at, player_id", id)
        .stream().map(r -> PlayerId.of(((Number) r.get("player_id")).longValue())).toList();
    if (participants.isEmpty()) participants = owner.stream().toList();
    return new SavedGame(game,new SavedGameContext(type,owner,type==GameType.HUMAN_VS_COMPUTER?Optional.of(computerConfiguration(id, game)):Optional.empty(), participants));
  }
  private ComputerGameConfiguration computerConfiguration(String id, ChessGame game) {
    var r=jdbc.queryForList("SELECT * FROM computer_game_configuration WHERE game_id=?",id).stream().findFirst().orElseThrow(()->new IllegalStateException("Missing computer configuration for "+id));
    return new ComputerGameConfiguration((String)r.get("human_name"),PieceColor.valueOf((String)r.get("human_color")),game.timeControl().orElse(TimeControl.unlimited()),Optional.of(Fen.of(fenService.fromSnapshot(game.initialPosition()).getValue())),(String)r.get("engine_id"),Duration.ofMillis(((Number)r.get("engine_thinking_ms")).longValue()));
  }
  @Override public List<SavedGameSummary> listSummaries(PlayerId ownerId) {
    if (!availability.isAvailable()) return fallback.listSummaries(ownerId);
    return jdbc.queryForList("SELECT g.id,g.game_type,g.white_name,g.black_name,g.status,g.result,(SELECT COUNT(*) FROM game_moves m WHERE m.game_id=g.id) moves_count,g.updated_at FROM games g LEFT JOIN game_participants gp ON gp.game_id=g.id WHERE gp.player_id=? OR g.owner_player_id=? OR (EXISTS (SELECT 1 FROM players p WHERE p.id=? AND p.player_type='SYSTEM' AND p.external_provider='LICHESS') AND EXISTS (SELECT 1 FROM computer_game_configuration c WHERE c.game_id=g.id AND c.engine_id='knightshade')) GROUP BY g.id ORDER BY g.updated_at DESC",ownerId.value(),ownerId.value(),ownerId.value()).stream().map(r->new SavedGameSummary(new GameId(UUID.fromString((String)r.get("id"))),GameType.valueOf((String)r.get("game_type")),(String)r.get("white_name"),(String)r.get("black_name"),"FINISHED".equals(r.get("status")),optionalEnum(GameResult.class,r.get("result")),((Number)r.get("moves_count")).intValue(),Instant.ofEpochMilli(((Number)r.get("updated_at")).longValue()))).toList();
  }
  @Override public boolean deleteById(GameId gameId) { if(!availability.isAvailable()) return fallback.deleteById(gameId); return jdbc.update("DELETE FROM games WHERE id=?",gameId.value().toString())>0; }

  /** Returns positions reached by real moves across every persisted game and player. */
  @Override @Transactional(readOnly = true) public List<MemoryGamePosition> findAllPlayedPositions() {
    if (!availability.isAvailable()) return List.of();
    return jdbc.queryForList(
            "SELECT game_id, ply_index, resulting_fen FROM game_moves ORDER BY game_id, ply_index")
        .stream()
        .map(row -> normalizePlayedPosition((String) row.get("game_id"),
            ((Number) row.get("ply_index")).intValue(), (String) row.get("resulting_fen")))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<MemoryGamePosition> normalizePlayedPosition(String gameId, int plyIndex, String rawFen) {
    try {
      // Re-serializing through the domain snapshot rejects malformed/corrupt FEN rows and
      // produces the canonical representation consumed by application clients.
      String normalized = fenService.fromSnapshot(rulesEngine.positionFrom(Fen.of(rawFen))).getValue();
      return Optional.of(new MemoryGamePosition(gameId + ":" + plyIndex, normalized));
    } catch (RuntimeException invalidFen) {
      return Optional.empty();
    }
  }

  private Ply toPly(java.util.Map<String,Object> r, com.escontrela.lastmove.domain.game.PositionSnapshot previousPosition) {
    Square from = Square.of((String) r.get("move_from"));
    Square to = Square.of((String) r.get("move_to"));
    boolean capture = ((Number) r.get("capture")).intValue() != 0;
    boolean enPassant = ((Number) r.get("en_passant")).intValue() != 0;
    MoveDescriptor move = new MoveDescriptor(from, to, SanMove.of((String) r.get("san")), capture,
        ((Number) r.get("castle")).intValue() != 0, enPassant,
        Optional.ofNullable((String) r.get("promotion")).map(PieceType::valueOf));
    Optional<com.escontrela.lastmove.domain.game.PositionPiece> captured = capture
        ? previousPosition.pieces().stream()
            .filter(piece -> piece.square().equals(enPassant ? Square.of(to.getFile(), from.getRank()) : to))
            .findFirst()
        : Optional.empty();
    return new Ply(UUID.fromString((String) r.get("ply_id")), move,
        rulesEngine.positionFrom(Fen.of((String) r.get("resulting_fen"))),
        ((Number) r.get("move_number")).intValue(), PieceColor.valueOf((String) r.get("moving_color")), captured);
  }
  private static Optional<Duration> optionalDuration(Object value) { return Optional.ofNullable((Number)value).map(n->Duration.ofMillis(n.longValue())); }
  private static GameClockSnapshot clock(Object white,Object black){ return new GameClockSnapshot(optionalDuration(white),optionalDuration(black)); }
  private static Long millis(Optional<Duration> duration){ return duration.map(Duration::toMillis).orElse(null); }
  private static <T extends Enum<T>> Optional<T> optionalEnum(Class<T> type,Object value){ return Optional.ofNullable((String)value).map(v->Enum.valueOf(type,v)); }
}
