package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.escontrela.lastmove.domain.game.GameId;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates durable Arena state with Lichess streams. It never treats a closed game stream as a finished game. */
@Service
public final class LichessArenaService {
  private static final Logger log=LoggerFactory.getLogger(LichessArenaService.class);
  private static final Duration STREAM_RECONNECT_DELAY=Duration.ofSeconds(2);
  private static final Duration RESILIENCE_INTERVAL=Duration.ofMinutes(1);
  private final LichessArenaRepository arena; private final KnightshadeArenaSettingsRepository settings; private final LichessBotClient client; private final ApplicationEventPublisher events;
  @org.springframework.beans.factory.annotation.Autowired(required=false) private LichessArenaTurnService turns;
  @org.springframework.beans.factory.annotation.Autowired(required=false) private BotChallengeCycleService botCycle;
  private volatile LichessBotClient.StreamHandle accountStream; private final Map<String,LichessBotClient.StreamHandle> gameStreams=new ConcurrentHashMap<>();
  private final Set<String> processedGameLifecycleEvents=ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService reconnects; private final Duration reconnectDelay;
  private final AtomicBoolean accountReconnectScheduled=new AtomicBoolean(); private final Set<String> gameReconnects=ConcurrentHashMap.newKeySet();
  private volatile ArenaTournamentListState tournamentListState=ArenaTournamentListState.DISCONNECTED;
  private volatile Optional<String> tournamentListError=Optional.empty();
  @org.springframework.beans.factory.annotation.Autowired
  public LichessArenaService(LichessArenaRepository arena,KnightshadeArenaSettingsRepository settings,LichessBotClient client,ApplicationEventPublisher events){this(arena,settings,client,events,STREAM_RECONNECT_DELAY,Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"lichess-reconnect");t.setDaemon(true);return t;}));}
  LichessArenaService(LichessArenaRepository arena,KnightshadeArenaSettingsRepository settings,LichessBotClient client,ApplicationEventPublisher events,Duration reconnectDelay,ScheduledExecutorService reconnects){this.arena=Objects.requireNonNull(arena);this.settings=Objects.requireNonNull(settings);this.client=Objects.requireNonNull(client);this.events=Objects.requireNonNull(events);this.reconnectDelay=Objects.requireNonNull(reconnectDelay);this.reconnects=Objects.requireNonNull(reconnects);}
  public synchronized ArenaConnection connect(){String token=token(); Instant now=Instant.now(); arena.saveConnection(new ArenaConnection(ArenaConnectionStatus.CONNECTING,Optional.empty(),Optional.empty(),Optional.empty(),now)); publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Connecting"); close(accountStream); accountStream=client.streamEvents(token,this::onAccountEvent,this::onAccountStreamClosed); ArenaConnection value=new ArenaConnection(ArenaConnectionStatus.CONNECTED,Optional.empty(),Optional.of(now),Optional.empty(),now);arena.saveConnection(value);publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Connected"); return value;}
  public synchronized ArenaConnection disconnect(){close(accountStream);accountStream=null;gameStreams.values().forEach(this::close);gameStreams.clear();tournamentListState=ArenaTournamentListState.DISCONNECTED;tournamentListError=Optional.empty();Instant now=Instant.now();ArenaConnection value=new ArenaConnection(ArenaConnectionStatus.DISCONNECTED,Optional.empty(),arena.connection().connectedAt(),Optional.of(now),now);arena.saveConnection(value);publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Disconnected");return value;}
  public ArenaConnection connection(){return arena.connection();} public List<ArenaChallenge> challenges(){return arena.listChallenges();} public List<ArenaGame> activeGames(){return arena.listActiveGames();}
  public Optional<ArenaGame> gameForLocal(GameId localGameId){return arena.listActiveGames().stream().filter(game->game.localGameId().filter(localGameId::equals).isPresent()).findFirst();}
  public List<ArenaTournament> tournaments(){return arena.listTournaments();}
  public ArenaTournamentListState tournamentListState(){return tournamentListState;}
  public Optional<String> tournamentListError(){return tournamentListError;}
  public Optional<LichessBotAccount> account(){return settings.findValidatedBotAccount();}
  public int maximumConcurrentGames(){return settings.loadSettings().maximumConcurrentGames();}
  public boolean automaticChallengeAcceptance(){return settings.loadSettings().automaticChallengeAcceptance();}
  public BotChallengeCycle botChallengeCycle(){return botCycle == null ? BotChallengeCycle.idle() : botCycle.cycle();}
  public List<LichessBotCandidate> onlineBots(){return botCycle == null ? List.of() : botCycle.bots();}
  public Optional<String> onlineBotsError(){return botCycle == null ? Optional.empty() : botCycle.botError();}
  public List<LichessBotCandidate> refreshOnlineBots(){if(botCycle==null) return List.of(); return botCycle.refreshBots();}
  public BotChallengeCycle startBotChallengeCycle(BotChallengeConfiguration configuration){if(connection().status()!=ArenaConnectionStatus.CONNECTED) throw new IllegalStateException("Connect Knightshade Arena before starting bot challenges.");return botCycle.start(configuration);}
  public BotChallengeCycle stopBotChallengeCycle(){return botCycle == null ? BotChallengeCycle.idle() : botCycle.stop();}
  /** Sends one user-requested challenge without entering the autonomous challenge cycle. */
  public synchronized LichessChallengeSubmission challengeBot(LichessBotCandidate bot, BotChallengeConfiguration configuration) {
    if (connection().status() != ArenaConnectionStatus.CONNECTED) throw new IllegalStateException("Connect Knightshade Arena before challenging a bot.");
    if (botCycle != null && botCycle.cycle().active()) throw new IllegalStateException("Stop the autonomous challenge cycle before challenging a bot manually.");
    if (bot == null || !bot.available()) throw new IllegalArgumentException("That bot is not currently available.");
    LichessChallengeSubmission submission = client.challengeBot(token(), bot.username(), configuration);
    submission.gameId().ifPresent(gameId -> registerStartedManualGame(gameId, submission.challengeId()));
    return submission;
  }
  /**
   * Reconciles Lichess' public Arena schedule into durable bot-eligible tournament rows.
   * A failed refresh deliberately keeps the last persisted schedule visible to the future UI.
   */
  public synchronized List<ArenaTournament> refreshTournaments(){
    if(connection().status()!=ArenaConnectionStatus.CONNECTED){
      tournamentListState=ArenaTournamentListState.DISCONNECTED;
      tournamentListError=Optional.of("Connect Knightshade Arena before loading tournaments.");
      return tournaments();
    }
    tournamentListState=ArenaTournamentListState.LOADING;
    tournamentListError=Optional.empty();
    try {
      Instant now=Instant.now();
      for(LichessTournamentSnapshot snapshot:client.currentTournaments(token())) {
        Optional<ArenaTournament> existing=arena.findTournament(snapshot.id());
        if(!snapshot.botsAllowed()&&existing.isEmpty()) continue;
        ArenaTournament tournament=existing.map(current->current.reconcile(snapshot,now))
            .orElseGet(()->ArenaTournament.discovered(snapshot,now));
        arena.saveTournament(tournament);
      }
      List<ArenaTournament> result=tournaments();
      tournamentListState=result.isEmpty()?ArenaTournamentListState.EMPTY:ArenaTournamentListState.READY;
      publish(LichessArenaEvent.Type.TOURNAMENTS_UPDATED,"",Integer.toString(result.size()));
      return result;
    } catch(LichessTournamentRequestException failure) {
      tournamentListState=ArenaTournamentListState.ERROR;
      tournamentListError=Optional.of(failure.getMessage());
      publish(LichessArenaEvent.Type.TOURNAMENTS_FAILED,"",failure.getMessage());
      return tournaments();
    } catch(RuntimeException failure) {
      tournamentListState=ArenaTournamentListState.ERROR;
      tournamentListError=Optional.of("Could not load Lichess tournaments.");
      publish(LichessArenaEvent.Type.TOURNAMENTS_FAILED,"","Could not load Lichess tournaments.");
      return tournaments();
    }
  }
  public synchronized void reconcileCurrentGames(){Instant reconciliationStarted=Instant.now();JsonNode response=client.currentGames(token());Set<String> current=new HashSet<>();response.path("nowPlaying").forEach(node->{String id=node.path("gameId").asText();if(id.isBlank())return;current.add(id);ArenaGame game=arena.findGame(id).orElseGet(()->newGame(id,Optional.empty(),optionalText(node,"fullId").map(v->"https://lichess.org/"+v),tournamentId(node)));arena.saveGame(game);if(!gameStreams.containsKey(id))openGameStream(id);});for(ArenaGame game:arena.listActiveGames())if((game.status()==ArenaGameStatus.STARTED||game.status()==ArenaGameStatus.ACTIVE||game.status()==ArenaGameStatus.STREAM_CLOSED)&&!current.contains(game.lichessGameId())&&!game.updatedAt().isAfter(reconciliationStarted)){finishGame(game.lichessGameId(),"Reconciled as finished");}}
  public void accept(String id){if(!arena.reserveChallenge(id,settings.loadSettings().maximumConcurrentGames())){decide(id,ArenaChallengeDecision.DECLINED,"Maximum concurrent games reached");client.declineChallenge(token(),id,"later");return;}try{client.acceptChallenge(token(),id);decide(id,ArenaChallengeDecision.ACCEPTED,null);}catch(RuntimeException failure){decide(id,ArenaChallengeDecision.FAILED,failure.getMessage());throw failure;}}
  public void decline(String id,String reason){client.declineChallenge(token(),id,reason);decide(id,ArenaChallengeDecision.DECLINED,reason);}
  public void sendMove(String gameId,String uci){client.sendMove(token(),gameId,uci);}
  private void onAccountEvent(JsonNode event){try{switch(event.path("type").asText()){case "challenge"->onChallenge(event.path("challenge"));case "challengeCanceled"->{String id=event.path("challenge").path("id").asText();decide(id,ArenaChallengeDecision.CANCELED,"Canceled on Lichess");if(botCycle!=null)botCycle.onChallengeCanceled(id,"Challenge canceled or declined by Lichess.");}case "gameStart"->startGame(event.path("game"));case "gameFinish"->finishGame(gameId(event.path("game")),"Finished");case "tournament","tournamentStart","tournamentFinish"->refreshTournaments();default->{} }}catch(RuntimeException failure){arena.saveConnection(error(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"",failure.getMessage());}}
  private void onChallenge(JsonNode node){Instant now=Instant.now();JsonNode challenger=node.path("challenger"),clock=node.path("timeControl");ArenaChallenge value=new ArenaChallenge(node.path("id").asText(),optionalText(challenger,"id"),text(challenger,"name","Unknown"),optionalInt(challenger,"rating"),text(node.path("variant"),"key","standard"),node.path("rated").asBoolean(),optionalInt(clock,"limit"),optionalInt(clock,"increment"),ArenaChallengeDecision.RECEIVED,Optional.empty(),now,Optional.empty(),now);arena.saveChallenge(value);publish(LichessArenaEvent.Type.CHALLENGE_RECEIVED,value.id(),value.challengerName());if(settings.loadSettings().automaticChallengeAcceptance()&&"standard".equalsIgnoreCase(value.variant()))accept(value.id());else if(!"standard".equalsIgnoreCase(value.variant()))decline(value.id(),"Only standard chess is currently supported");}
  private void startGame(JsonNode node){String id=gameId(node);if(id.isBlank())return;Optional<String> challengeId=optionalText(node,"challengeId");Optional<String> tournamentId=tournamentId(node);ArenaGame game=arena.findGame(id).orElseGet(()->newGame(id,challengeId,optionalText(node,"url"),tournamentId));if(tournamentId.isPresent()&&!tournamentId.equals(game.tournamentId()))game=withTournament(game,tournamentId);arena.saveGame(game);if(botCycle!=null){Set<String> identities=new HashSet<>();playerId(node.path("opponent")).ifPresent(identities::add);playerId(node.path("white")).ifPresent(identities::add);playerId(node.path("black")).ifPresent(identities::add);botCycle.onGameStarted(id,challengeId,identities);}if(!gameStreams.containsKey(id))openGameStream(id);if(processedGameLifecycleEvents.add("gameStart:"+id)){tournamentId.ifPresent(value->publish(LichessArenaEvent.Type.TOURNAMENT_PAIRING_RECEIVED,id,value));publish(LichessArenaEvent.Type.GAME_STARTED,id,tournamentId.map(value->"Tournament "+value).orElse("Started"));}}
  private void registerStartedManualGame(String gameId, Optional<String> challengeId) { ArenaGame game=arena.findGame(gameId).orElseGet(()->newGame(gameId,challengeId,Optional.empty(),Optional.empty())); arena.saveGame(game); if(!gameStreams.containsKey(gameId))openGameStream(gameId); if(processedGameLifecycleEvents.add("gameStart:"+gameId)) publish(LichessArenaEvent.Type.GAME_STARTED,gameId,"Started"); }
  private void finishGame(String id,String detail){if(id.isBlank())return;ArenaGame old=arena.findGame(id).orElse(null);if(old==null)return;close(gameStreams.remove(id));Instant now=Instant.now();arena.saveGame(new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),old.tournamentId(),old.gameUrl(),old.whiteLichessId(),old.blackLichessId(),old.botColor(),ArenaGameStatus.FINISHED,Optional.empty(),old.startedAt(),Optional.of(now),now));if(botCycle!=null)botCycle.onGameFinished(id,detail);if(processedGameLifecycleEvents.add("gameFinish:"+id))publish(LichessArenaEvent.Type.GAME_FINISHED,id,detail);}
  private void openGameStream(String gameId){close(gameStreams.put(gameId,client.streamGame(token(),gameId,event->onGameEvent(gameId,event),failure->onGameStreamClosed(gameId,failure))));}
  private void onGameEvent(String id, JsonNode event) {
    ArenaGame old = arena.findGame(id).orElse(null);
    if (old == null) return;
    String type = event.path("type").asText();
    if (!"gameFull".equals(type) && !"gameState".equals(type)) return;
    Instant now = Instant.now();
    ArenaGameStatus status = old.status() == ArenaGameStatus.FINISHED ? ArenaGameStatus.FINISHED : ArenaGameStatus.ACTIVE;
    Optional<String> whiteId = old.whiteLichessId(), blackId = old.blackLichessId();
    Optional<com.escontrela.lastmove.domain.common.PieceColor> botColor = old.botColor();
    if ("gameFull".equals(type)) {
      whiteId = playerId(event.path("white"));
      blackId = playerId(event.path("black"));
      String bot = account().map(LichessBotAccount::id).orElse("");
      if (whiteId.filter(bot::equalsIgnoreCase).isPresent()) botColor = Optional.of(com.escontrela.lastmove.domain.common.PieceColor.WHITE);
      else if (blackId.filter(bot::equalsIgnoreCase).isPresent()) botColor = Optional.of(com.escontrela.lastmove.domain.common.PieceColor.BLACK);
      if (botCycle != null) { Set<String> identities=new HashSet<>();whiteId.ifPresent(identities::add);blackId.ifPresent(identities::add);botCycle.onGameStarted(id, optionalText(event.path("challenge"), "id").or(() -> optionalText(event, "challengeId")),identities); }
    }
    Optional<String> tournamentId = tournamentId(event).or(() -> old.tournamentId());
    ArenaGame observed = new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),tournamentId,old.gameUrl(),whiteId,blackId,botColor,status,Optional.empty(),old.startedAt(),old.finishedAt(),now);
    arena.saveGame(observed);
    if (turns != null) {
      turns.consume(id,event,old.localGameId(),tournamentId,account().orElseThrow(()->new IllegalStateException("Arena bot account is not validated")),token(),client);
      Optional<String> finalWhiteId = whiteId, finalBlackId = blackId;
      Optional<com.escontrela.lastmove.domain.common.PieceColor> finalBotColor = botColor;
      turns.localGameId(id).ifPresent(local->arena.saveGame(new ArenaGame(old.lichessGameId(),Optional.of(local),old.challengeId(),tournamentId,old.gameUrl(),finalWhiteId,finalBlackId,finalBotColor,status,Optional.empty(),old.startedAt(),old.finishedAt(),Instant.now())));
    }
    publish(LichessArenaEvent.Type.GAME_UPDATED,id,type);
    JsonNode state = "gameFull".equals(type) ? event.path("state") : event;
    if (isTerminalGameState(state)) finishGame(id, "Finished: " + state.path("status").asText());
  }
  private void onAccountStreamClosed(Throwable failure){if(connection().status()==ArenaConnectionStatus.DISCONNECTED||failure instanceof InterruptedException)return;arena.saveConnection(reconnecting(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Account stream interrupted; reconnecting");scheduleAccountReconnect();}
  private void onGameStreamClosed(String id,Throwable failure){if(connection().status()==ArenaConnectionStatus.DISCONNECTED||failure instanceof InterruptedException)return;ArenaGame old=arena.findGame(id).orElse(null);if(old==null||old.status()==ArenaGameStatus.FINISHED)return;Instant now=Instant.now();arena.saveGame(new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),old.tournamentId(),old.gameUrl(),old.whiteLichessId(),old.blackLichessId(),old.botColor(),ArenaGameStatus.STREAM_CLOSED,Optional.ofNullable(failure.getMessage()),old.startedAt(),old.finishedAt(),now));publish(LichessArenaEvent.Type.GAME_UPDATED,id,"Stream interrupted; reconnecting");scheduleGameReconnect(id);}
  private static boolean isTerminalGameState(JsonNode state){String status=state.path("status").asText();return !status.isBlank()&&!"started".equals(status);}
  private void scheduleAccountReconnect(){if(!accountReconnectScheduled.compareAndSet(false,true))return;reconnects.schedule(()->{accountReconnectScheduled.set(false);synchronized(this){if(connection().status()==ArenaConnectionStatus.DISCONNECTED)return;try{connect();refreshTournaments();reconcileCurrentGames();}catch(RuntimeException failure){arena.saveConnection(reconnecting(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Reconnect failed; retrying");scheduleAccountReconnect();}}},reconnectDelay.toMillis(),TimeUnit.MILLISECONDS);}
  private void scheduleGameReconnect(String id){if(!gameReconnects.add(id))return;reconnects.schedule(()->{gameReconnects.remove(id);synchronized(this){if(connection().status()==ArenaConnectionStatus.DISCONNECTED)return;ArenaGame game=arena.findGame(id).orElse(null);if(game==null||game.status()==ArenaGameStatus.FINISHED)return;openGameStream(id);publish(LichessArenaEvent.Type.GAME_UPDATED,id,"Game stream reconnecting");}},reconnectDelay.toMillis(),TimeUnit.MILLISECONDS);}
  @PreDestroy public void closeOnApplicationExit(){try{disconnect();}catch(RuntimeException ignored){/* Persistence may already be shutting down. */}finally{reconnects.shutdownNow();}}
  /** One-minute safety net: streams are primary, this reconciliation closes every missed-event gap. */
  @PostConstruct void reconnectOnStartup(){reconnects.scheduleAtFixedRate(this::superviseResilience,RESILIENCE_INTERVAL.toMillis(),RESILIENCE_INTERVAL.toMillis(),TimeUnit.MILLISECONDS);if(settings.loadSettings().autoReconnect()&&settings.findBotToken().isPresent()) reconnects.execute(()->{try{connect();superviseResilience();}catch(RuntimeException failure){arena.saveConnection(error(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Auto-reconnect failed: "+failure.getMessage());}});}
  synchronized void superviseResilience(){if(connection().status()==ArenaConnectionStatus.DISCONNECTED)return;try{if(connection().status()!=ArenaConnectionStatus.CONNECTED)connect();reconcileCurrentGames();if(botCycle!=null){recoverCycleFromKnownGames();Set<String> active=arena.listActiveGames().stream().filter(game->game.status()==ArenaGameStatus.STARTED||game.status()==ArenaGameStatus.ACTIVE||game.status()==ArenaGameStatus.STREAM_CLOSED).map(ArenaGame::lichessGameId).collect(java.util.stream.Collectors.toSet());botCycle.reconcileActiveGames(active);botCycle.resume();}publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Lichess resilience check completed");}catch(RuntimeException failure){log.warn("Lichess resilience check failed; scheduling reconnect",failure);arena.saveConnection(reconnecting(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Resilience check failed; reconnecting");scheduleAccountReconnect();}}
  private void recoverCycleFromKnownGames(){BotChallengeCycle state=botCycle.cycle();if(!state.active()||state.currentGameId().isPresent()||state.currentBotId().isEmpty())return;String opponent=state.currentBotId().orElseThrow();arena.listActiveGames().stream().filter(game->!game.startedAt().isBefore(state.updatedAt().minusSeconds(10))).filter(game->game.whiteLichessId().filter(opponent::equalsIgnoreCase).isPresent()||game.blackLichessId().filter(opponent::equalsIgnoreCase).isPresent()).max(Comparator.comparing(ArenaGame::startedAt)).ifPresent(game->{Set<String> players=new HashSet<>();game.whiteLichessId().ifPresent(players::add);game.blackLichessId().ifPresent(players::add);botCycle.onGameStarted(game.lichessGameId(),game.challengeId(),players);if(game.status()==ArenaGameStatus.FINISHED)botCycle.onGameFinished(game.lichessGameId(),"Recovered completed game");});}
  private void decide(String id,ArenaChallengeDecision decision,String reason){arena.findChallenge(id).ifPresent(old->{Instant now=Instant.now();arena.saveChallenge(new ArenaChallenge(old.id(),old.challengerId(),old.challengerName(),old.challengerRating(),old.variant(),old.rated(),old.clockLimitSeconds(),old.clockIncrementSeconds(),decision,Optional.ofNullable(reason),old.receivedAt(),Optional.of(now),now));publish(LichessArenaEvent.Type.CHALLENGE_DECIDED,id,decision.name());});}
  private ArenaConnection error(Throwable failure){Instant now=Instant.now();return new ArenaConnection(ArenaConnectionStatus.ERROR,Optional.ofNullable(failure.getMessage()).or(()->Optional.of("Lichess stream failed")),arena.connection().connectedAt(),Optional.empty(),now);}
  private ArenaConnection reconnecting(Throwable failure){Instant now=Instant.now();return new ArenaConnection(ArenaConnectionStatus.RECONNECTING,Optional.ofNullable(failure.getMessage()).or(()->Optional.of("Lichess stream closed")),arena.connection().connectedAt(),Optional.empty(),now);}private ArenaGame newGame(String id,Optional<String> challengeId,Optional<String> url,Optional<String> tournamentId){Instant now=Instant.now();return new ArenaGame(id,Optional.empty(),challengeId,tournamentId,url,Optional.empty(),Optional.empty(),Optional.empty(),ArenaGameStatus.STARTED,Optional.empty(),now,Optional.empty(),now);}private ArenaGame withTournament(ArenaGame game,Optional<String> tournamentId){return new ArenaGame(game.lichessGameId(),game.localGameId(),game.challengeId(),tournamentId,game.gameUrl(),game.whiteLichessId(),game.blackLichessId(),game.botColor(),game.status(),game.lastError(),game.startedAt(),game.finishedAt(),Instant.now());}private String token(){return settings.findBotToken().orElseThrow(()->new IllegalStateException("Configure a Lichess bot token before connecting Arena."));}private void publish(LichessArenaEvent.Type type,String id,String detail){events.publishEvent(new LichessArenaEvent(type,id,detail));}private void close(LichessBotClient.StreamHandle handle){if(handle!=null)handle.close();}private static String text(JsonNode n,String k,String fallback){String v=n.path(k).asText();return v.isBlank()?fallback:v;}private static Optional<String> optionalText(JsonNode n,String k){String v=n.path(k).asText();return v.isBlank()?Optional.empty():Optional.of(v);}private static String gameId(JsonNode game){return optionalText(game,"id").or(()->optionalText(game,"gameId")).orElse("");}private static Optional<String> tournamentId(JsonNode event){Optional<String> direct=optionalText(event,"tournamentId");if(direct.isPresent())return direct;JsonNode tournament=event.path("tournament");return tournament.isTextual()?optionalText(event,"tournament"):optionalText(tournament,"id");}private static Optional<String> playerId(JsonNode player){JsonNode identity=player.has("user")?player.path("user"):player;String value=identity.path("id").asText();if(value.isBlank())value=identity.path("name").asText();return value.isBlank()?Optional.empty():Optional.of(value);}private static Optional<Integer> optionalInt(JsonNode n,String k){return n.hasNonNull(k)?Optional.of(n.path(k).asInt()):Optional.empty();}
}
