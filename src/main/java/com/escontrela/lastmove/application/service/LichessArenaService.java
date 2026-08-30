package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
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

/** Coordinates durable Arena state with Lichess streams. It never treats a closed game stream as a finished game. */
@Service
public final class LichessArenaService {
  private static final Duration STREAM_RECONNECT_DELAY=Duration.ofSeconds(2);
  private final LichessArenaRepository arena; private final KnightshadeArenaSettingsRepository settings; private final LichessBotClient client; private final ApplicationEventPublisher events;
  @org.springframework.beans.factory.annotation.Autowired(required=false) private LichessArenaTurnService turns;
  private volatile LichessBotClient.StreamHandle accountStream; private final Map<String,LichessBotClient.StreamHandle> gameStreams=new ConcurrentHashMap<>();
  private final ScheduledExecutorService reconnects; private final Duration reconnectDelay;
  private final AtomicBoolean accountReconnectScheduled=new AtomicBoolean(); private final Set<String> gameReconnects=ConcurrentHashMap.newKeySet();
  @org.springframework.beans.factory.annotation.Autowired
  public LichessArenaService(LichessArenaRepository arena,KnightshadeArenaSettingsRepository settings,LichessBotClient client,ApplicationEventPublisher events){this(arena,settings,client,events,STREAM_RECONNECT_DELAY,Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"lichess-reconnect");t.setDaemon(true);return t;}));}
  LichessArenaService(LichessArenaRepository arena,KnightshadeArenaSettingsRepository settings,LichessBotClient client,ApplicationEventPublisher events,Duration reconnectDelay,ScheduledExecutorService reconnects){this.arena=Objects.requireNonNull(arena);this.settings=Objects.requireNonNull(settings);this.client=Objects.requireNonNull(client);this.events=Objects.requireNonNull(events);this.reconnectDelay=Objects.requireNonNull(reconnectDelay);this.reconnects=Objects.requireNonNull(reconnects);}
  public synchronized ArenaConnection connect(){String token=token(); Instant now=Instant.now(); arena.saveConnection(new ArenaConnection(ArenaConnectionStatus.CONNECTING,Optional.empty(),Optional.empty(),Optional.empty(),now)); publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Connecting"); close(accountStream); accountStream=client.streamEvents(token,this::onAccountEvent,this::onAccountStreamClosed); ArenaConnection value=new ArenaConnection(ArenaConnectionStatus.CONNECTED,Optional.empty(),Optional.of(now),Optional.empty(),now);arena.saveConnection(value);publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Connected"); return value;}
  public synchronized ArenaConnection disconnect(){close(accountStream);accountStream=null;gameStreams.values().forEach(this::close);gameStreams.clear();Instant now=Instant.now();ArenaConnection value=new ArenaConnection(ArenaConnectionStatus.DISCONNECTED,Optional.empty(),arena.connection().connectedAt(),Optional.of(now),now);arena.saveConnection(value);publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Disconnected");return value;}
  public ArenaConnection connection(){return arena.connection();} public List<ArenaChallenge> challenges(){return arena.listChallenges();} public List<ArenaGame> activeGames(){return arena.listActiveGames();}
  public Optional<LichessBotAccount> account(){return settings.findValidatedBotAccount();}
  public int maximumConcurrentGames(){return settings.loadSettings().maximumConcurrentGames();}
  public boolean automaticChallengeAcceptance(){return settings.loadSettings().automaticChallengeAcceptance();}
  public synchronized void reconcileCurrentGames(){JsonNode response=client.currentGames(token());Set<String> current=new HashSet<>();response.path("nowPlaying").forEach(node->{String id=node.path("gameId").asText();if(id.isBlank())return;current.add(id);ArenaGame game=arena.findGame(id).orElseGet(()->{Instant now=Instant.now();return new ArenaGame(id,Optional.empty(),Optional.empty(),optionalText(node,"fullId").map(v->"https://lichess.org/"+v),Optional.empty(),Optional.empty(),Optional.empty(),ArenaGameStatus.STARTED,Optional.empty(),now,Optional.empty(),now);});arena.saveGame(game);if(!gameStreams.containsKey(id))openGameStream(id);});for(ArenaGame game:arena.listActiveGames())if((game.status()==ArenaGameStatus.STARTED||game.status()==ArenaGameStatus.ACTIVE||game.status()==ArenaGameStatus.STREAM_CLOSED)&&!current.contains(game.lichessGameId())){Instant now=Instant.now();arena.saveGame(new ArenaGame(game.lichessGameId(),game.localGameId(),game.challengeId(),game.gameUrl(),game.whiteLichessId(),game.blackLichessId(),game.botColor(),ArenaGameStatus.FINISHED,Optional.empty(),game.startedAt(),Optional.of(now),now));publish(LichessArenaEvent.Type.GAME_FINISHED,game.lichessGameId(),"Reconciled as finished");}}
  public void accept(String id){if(!arena.reserveChallenge(id,settings.loadSettings().maximumConcurrentGames())){decide(id,ArenaChallengeDecision.DECLINED,"Maximum concurrent games reached");client.declineChallenge(token(),id,"later");return;}try{client.acceptChallenge(token(),id);decide(id,ArenaChallengeDecision.ACCEPTED,null);}catch(RuntimeException failure){decide(id,ArenaChallengeDecision.FAILED,failure.getMessage());throw failure;}}
  public void decline(String id,String reason){client.declineChallenge(token(),id,reason);decide(id,ArenaChallengeDecision.DECLINED,reason);}
  public void sendMove(String gameId,String uci){client.sendMove(token(),gameId,uci);}
  private void onAccountEvent(JsonNode event){try{switch(event.path("type").asText()){case "challenge"->onChallenge(event.path("challenge"));case "challengeCanceled"->decide(event.path("challenge").path("id").asText(),ArenaChallengeDecision.CANCELED,"Canceled on Lichess");case "gameStart"->startGame(event.path("game"));case "gameFinish"->finishGame(event.path("game").path("id").asText());default->{} }}catch(RuntimeException failure){arena.saveConnection(error(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"",failure.getMessage());}}
  private void onChallenge(JsonNode node){Instant now=Instant.now();JsonNode challenger=node.path("challenger"),clock=node.path("timeControl");ArenaChallenge value=new ArenaChallenge(node.path("id").asText(),optionalText(challenger,"id"),text(challenger,"name","Unknown"),optionalInt(challenger,"rating"),text(node.path("variant"),"key","standard"),node.path("rated").asBoolean(),optionalInt(clock,"limit"),optionalInt(clock,"increment"),ArenaChallengeDecision.RECEIVED,Optional.empty(),now,Optional.empty(),now);arena.saveChallenge(value);publish(LichessArenaEvent.Type.CHALLENGE_RECEIVED,value.id(),value.challengerName());if(settings.loadSettings().automaticChallengeAcceptance()&&"standard".equalsIgnoreCase(value.variant()))accept(value.id());else if(!"standard".equalsIgnoreCase(value.variant()))decline(value.id(),"Only standard chess is currently supported");}
  private void startGame(JsonNode node){String id=node.path("id").asText();if(id.isBlank())return;Instant now=Instant.now();ArenaGame game=new ArenaGame(id,Optional.empty(),optionalText(node,"challengeId"),optionalText(node,"url"),Optional.empty(),Optional.empty(),Optional.empty(),ArenaGameStatus.STARTED,Optional.empty(),now,Optional.empty(),now);arena.saveGame(game);openGameStream(id);publish(LichessArenaEvent.Type.GAME_STARTED,id,"Started");}
  private void finishGame(String id){if(id.isBlank())return;ArenaGame old=arena.findGame(id).orElse(null);if(old==null)return;Instant now=Instant.now();arena.saveGame(new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),old.gameUrl(),old.whiteLichessId(),old.blackLichessId(),old.botColor(),ArenaGameStatus.FINISHED,Optional.empty(),old.startedAt(),Optional.of(now),now));publish(LichessArenaEvent.Type.GAME_FINISHED,id,"Finished");}
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
    }
    ArenaGame observed = new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),old.gameUrl(),whiteId,blackId,botColor,status,Optional.empty(),old.startedAt(),old.finishedAt(),now);
    arena.saveGame(observed);
    if (turns != null) {
      turns.consume(id,event,old.localGameId(),account().orElseThrow(()->new IllegalStateException("Arena bot account is not validated")),token(),client);
      Optional<String> finalWhiteId = whiteId, finalBlackId = blackId;
      Optional<com.escontrela.lastmove.domain.common.PieceColor> finalBotColor = botColor;
      turns.localGameId(id).ifPresent(local->arena.saveGame(new ArenaGame(old.lichessGameId(),Optional.of(local),old.challengeId(),old.gameUrl(),finalWhiteId,finalBlackId,finalBotColor,status,Optional.empty(),old.startedAt(),old.finishedAt(),Instant.now())));
    }
    publish(LichessArenaEvent.Type.GAME_UPDATED,id,type);
  }
  private void onAccountStreamClosed(Throwable failure){if(connection().status()==ArenaConnectionStatus.DISCONNECTED||failure instanceof InterruptedException)return;arena.saveConnection(reconnecting(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Account stream interrupted; reconnecting");scheduleAccountReconnect();}
  private void onGameStreamClosed(String id,Throwable failure){if(connection().status()==ArenaConnectionStatus.DISCONNECTED||failure instanceof InterruptedException)return;ArenaGame old=arena.findGame(id).orElse(null);if(old==null||old.status()==ArenaGameStatus.FINISHED)return;Instant now=Instant.now();arena.saveGame(new ArenaGame(old.lichessGameId(),old.localGameId(),old.challengeId(),old.gameUrl(),old.whiteLichessId(),old.blackLichessId(),old.botColor(),ArenaGameStatus.STREAM_CLOSED,Optional.ofNullable(failure.getMessage()),old.startedAt(),old.finishedAt(),now));publish(LichessArenaEvent.Type.GAME_UPDATED,id,"Stream interrupted; reconnecting");scheduleGameReconnect(id);}
  private void scheduleAccountReconnect(){if(!accountReconnectScheduled.compareAndSet(false,true))return;reconnects.schedule(()->{accountReconnectScheduled.set(false);synchronized(this){if(connection().status()==ArenaConnectionStatus.DISCONNECTED)return;try{connect();reconcileCurrentGames();}catch(RuntimeException failure){arena.saveConnection(reconnecting(failure));publish(LichessArenaEvent.Type.CONNECTIVITY_CHANGED,"","Reconnect failed; retrying");scheduleAccountReconnect();}}},reconnectDelay.toMillis(),TimeUnit.MILLISECONDS);}
  private void scheduleGameReconnect(String id){if(!gameReconnects.add(id))return;reconnects.schedule(()->{gameReconnects.remove(id);synchronized(this){if(connection().status()==ArenaConnectionStatus.DISCONNECTED)return;ArenaGame game=arena.findGame(id).orElse(null);if(game==null||game.status()==ArenaGameStatus.FINISHED)return;openGameStream(id);publish(LichessArenaEvent.Type.GAME_UPDATED,id,"Game stream reconnecting");}},reconnectDelay.toMillis(),TimeUnit.MILLISECONDS);}
  @PreDestroy public void closeOnApplicationExit(){try{disconnect();}catch(RuntimeException ignored){/* Persistence may already be shutting down. */}finally{reconnects.shutdownNow();}}
  private void decide(String id,ArenaChallengeDecision decision,String reason){arena.findChallenge(id).ifPresent(old->{Instant now=Instant.now();arena.saveChallenge(new ArenaChallenge(old.id(),old.challengerId(),old.challengerName(),old.challengerRating(),old.variant(),old.rated(),old.clockLimitSeconds(),old.clockIncrementSeconds(),decision,Optional.ofNullable(reason),old.receivedAt(),Optional.of(now),now));publish(LichessArenaEvent.Type.CHALLENGE_DECIDED,id,decision.name());});}
  private ArenaConnection error(Throwable failure){Instant now=Instant.now();return new ArenaConnection(ArenaConnectionStatus.ERROR,Optional.ofNullable(failure.getMessage()).or(()->Optional.of("Lichess stream failed")),arena.connection().connectedAt(),Optional.empty(),now);}
  private ArenaConnection reconnecting(Throwable failure){Instant now=Instant.now();return new ArenaConnection(ArenaConnectionStatus.RECONNECTING,Optional.ofNullable(failure.getMessage()).or(()->Optional.of("Lichess stream closed")),arena.connection().connectedAt(),Optional.empty(),now);}private String token(){return settings.findBotToken().orElseThrow(()->new IllegalStateException("Configure a Lichess bot token before connecting Arena."));}private void publish(LichessArenaEvent.Type type,String id,String detail){events.publishEvent(new LichessArenaEvent(type,id,detail));}private void close(LichessBotClient.StreamHandle handle){if(handle!=null)handle.close();}private static String text(JsonNode n,String k,String fallback){String v=n.path(k).asText();return v.isBlank()?fallback:v;}private static Optional<String> optionalText(JsonNode n,String k){String v=n.path(k).asText();return v.isBlank()?Optional.empty():Optional.of(v);}private static Optional<String> playerId(JsonNode player){JsonNode identity=player.has("user")?player.path("user"):player;String value=identity.path("id").asText();if(value.isBlank())value=identity.path("name").asText();return value.isBlank()?Optional.empty():Optional.of(value);}private static Optional<Integer> optionalInt(JsonNode n,String k){return n.hasNonNull(k)?Optional.of(n.path(k).asInt()):Optional.empty();}
}
