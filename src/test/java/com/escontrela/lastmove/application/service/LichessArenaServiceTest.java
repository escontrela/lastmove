package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class LichessArenaServiceTest {
  @Test void automaticallyAcceptsStandardChallengesAndPersistsDecision() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(true, 2); FakeClient client = new FakeClient();
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c1\",\"rated\":true,\"variant\":{\"key\":\"standard\"},\"challenger\":{\"id\":\"u1\",\"name\":\"Alice\"}}}"));
    assertEquals(ArenaChallengeDecision.ACCEPTED, repo.challenges.get("c1").decision()); assertEquals("c1", client.accepted);
  }
  @Test void rejectsUnsupportedVariantWithVisibleReason() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(true, 2); FakeClient client = new FakeClient(); LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c2\",\"rated\":false,\"variant\":{\"key\":\"crazyhouse\"},\"challenger\":{\"name\":\"Bob\"}}}"));
    assertEquals(ArenaChallengeDecision.DECLINED, repo.challenges.get("c2").decision()); assertTrue(repo.challenges.get("c2").decisionReason().orElseThrow().contains("standard"));
  }
  @Test void declinesWhenCapacityReservationFails() throws Exception {
    FakeRepository repo = new FakeRepository(){ @Override public boolean reserveChallenge(String id,int max){return false;} }; FakeSettings settings = new FakeSettings(true, 1); FakeClient client = new FakeClient(); LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c3\",\"rated\":true,\"variant\":{\"key\":\"standard\"},\"challenger\":{\"name\":\"Capacity\"}}}"));
    assertEquals(ArenaChallengeDecision.DECLINED, repo.challenges.get("c3").decision());
  }
  @Test void reconnectsAccountAndGameStreamsAfterTransportClosure() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(false, 2); FakeClient client = new FakeClient();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}, Duration.ZERO, scheduler);
    try {
      service.connect();
      client.accountClosed.accept(new java.io.IOException("GOAWAY received"));
      await(() -> client.accountSubscriptions == 2);
      assertEquals(ArenaConnectionStatus.CONNECTED, service.connection().status());

      client.account.accept(new ObjectMapper().readTree("{\"type\":\"gameStart\",\"game\":{\"id\":\"g1\"}}"));
      assertEquals(1, client.gameSubscriptions.get("g1").intValue());
      client.gameClosed.get("g1").accept(new java.io.IOException("GOAWAY received"));
      await(() -> client.gameSubscriptions.get("g1") == 2);
    } finally {
      service.closeOnApplicationExit();
      scheduler.shutdownNow();
    }
  }
  @Test void doesNotReconnectAfterTerminalGameStateAndStreamClosure() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeClient client = new FakeClient();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    LichessArenaService service = new LichessArenaService(repo, new FakeSettings(false, 2), client, event -> {}, Duration.ZERO, scheduler);
    try {
      service.connect();
      client.account.accept(new ObjectMapper().readTree("{\"type\":\"gameStart\",\"game\":{\"id\":\"g-terminal\"}}"));
      client.gameEvents.get("g-terminal").accept(new ObjectMapper().readTree("{\"type\":\"gameState\",\"status\":\"mate\",\"moves\":\"e2e4 e7e5\"}"));
      client.gameClosed.get("g-terminal").accept(new java.io.IOException("EOF after finished game"));
      assertEquals(ArenaGameStatus.FINISHED, repo.findGame("g-terminal").orElseThrow().status());
      Thread.sleep(50);
      assertEquals(1, client.gameSubscriptions.get("g-terminal").intValue());
    } finally { service.closeOnApplicationExit(); scheduler.shutdownNow(); }
  }
  @Test void refreshesAndReconcilesBotEligibleTournaments() {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(false, 2); FakeClient client = new FakeClient();
    client.tournaments = List.of(tournament("standard", true, ArenaTournamentStatus.CREATED), tournament("chess960", true, ArenaTournamentStatus.CREATED));
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {});
    service.connect();

    List<ArenaTournament> first = service.refreshTournaments();

    assertEquals(ArenaTournamentListState.READY, service.tournamentListState());
    assertEquals(2, first.size());
    assertEquals(ArenaTournamentRegistrationStatus.AVAILABLE, repo.tournaments.get("t-standard").registrationStatus());
    assertEquals(ArenaTournamentRegistrationStatus.INCOMPATIBLE, repo.tournaments.get("t-chess960").registrationStatus());

    repo.tournaments.put("t-standard", repo.tournaments.get("t-standard").withRegistration(ArenaTournamentRegistrationStatus.JOINING, Optional.empty(), Instant.now()));
    client.tournaments = List.of(tournament("standard", true, ArenaTournamentStatus.STARTED));
    service.refreshTournaments();

    assertEquals(ArenaTournamentRegistrationStatus.JOINING, repo.tournaments.get("t-standard").registrationStatus());
    assertEquals(ArenaTournamentStatus.STARTED, repo.tournaments.get("t-standard").remoteStatus());

    client.tournaments = List.of(tournament("standard", false, ArenaTournamentStatus.STARTED));
    service.refreshTournaments();
    assertEquals(ArenaTournamentRegistrationStatus.NOT_ELIGIBLE, repo.tournaments.get("t-standard").registrationStatus());
  }
  @Test void preservesPersistedTournamentsAndReportsErrorWhenTheScheduleFails() {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(false, 2); FakeClient client = new FakeClient();
    client.tournaments = List.of(tournament("standard", true, ArenaTournamentStatus.CREATED));
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect(); service.refreshTournaments();
    client.tournamentFailure = new LichessTournamentRequestException(LichessTournamentRequestException.Kind.RATE_LIMITED, "Wait one minute");

    List<ArenaTournament> retained = service.refreshTournaments();

    assertEquals(ArenaTournamentListState.ERROR, service.tournamentListState());
    assertEquals("Wait one minute", service.tournamentListError().orElseThrow());
    assertEquals(List.of("t-standard"), retained.stream().map(ArenaTournament::lichessTournamentId).toList());
  }
  @Test void keepsTournamentListDisconnectedUntilArenaIsConnected() {
    FakeRepository repo = new FakeRepository(); FakeClient client = new FakeClient();
    LichessArenaService service = new LichessArenaService(repo, new FakeSettings(false, 2), client, event -> {});

    assertTrue(service.refreshTournaments().isEmpty());
    assertEquals(ArenaTournamentListState.DISCONNECTED, service.tournamentListState());
    assertEquals(0, client.tournamentRequests);
  }
  @Test void associatesArenaPairingWithThePersistedTournamentAndDoesNotDuplicateItsLifecycle() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(false, 2); FakeClient client = new FakeClient();
    List<LichessArenaEvent> published = new ArrayList<>();
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> published.add((LichessArenaEvent) event)); service.connect();

    JsonNode pairing = new ObjectMapper().readTree("{\"type\":\"gameStart\",\"game\":{\"gameId\":\"g-tournament\",\"tournamentId\":\"botblitz\"}}");
    client.account.accept(pairing);
    client.account.accept(pairing);

    ArenaGame game = repo.findGame("g-tournament").orElseThrow();
    assertEquals(Optional.of("botblitz"), game.tournamentId());
    assertEquals(1, client.gameSubscriptions.get("g-tournament"));
    assertEquals(1, published.stream().filter(event -> event.type() == LichessArenaEvent.Type.TOURNAMENT_PAIRING_RECEIVED).count());
    assertEquals(1, published.stream().filter(event -> event.type() == LichessArenaEvent.Type.GAME_STARTED).count());

    client.gameClosed.get("g-tournament").accept(new java.io.IOException("temporary transport failure"));
    assertEquals(ArenaGameStatus.STREAM_CLOSED, repo.findGame("g-tournament").orElseThrow().status());
  }
  private static LichessTournamentSnapshot tournament(String variant, boolean botsAllowed, ArenaTournamentStatus status) {
    return new LichessTournamentSnapshot("t-" + variant, "Bot " + variant, status, variant, true, 180, 2, 60, 12,
        Optional.of(1200), Optional.of(2400), botsAllowed, Optional.of(Instant.parse("2026-08-30T12:00:00Z")),
        Optional.empty(), Optional.of(120), Optional.of("https://lichess.org/tournament/t-" + variant));
  }
  private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException { for (int i=0;i<200&&!condition.getAsBoolean();i++) Thread.sleep(10); assertTrue(condition.getAsBoolean(), "condition was not met"); }
  private static final class FakeSettings implements KnightshadeArenaSettingsRepository { final KnightshadeArenaSettings value; FakeSettings(boolean auto,int max){value=new KnightshadeArenaSettings(max,auto);} public KnightshadeArenaSettings loadSettings(){return value;} public void saveSettings(KnightshadeArenaSettings s){} public Optional<String> findBotToken(){return Optional.of("token");} public void saveBotToken(String t){} public void deleteBotToken(){} public Optional<LichessBotAccount> findValidatedBotAccount(){return Optional.of(new LichessBotAccount("bot","Knightshade"));} public void saveValidatedBotAccount(LichessBotAccount a){} }
  private static class FakeRepository implements LichessArenaRepository { final Map<String,ArenaChallenge> challenges=new LinkedHashMap<>(); final Map<String,ArenaGame> games=new LinkedHashMap<>(); final Map<String,ArenaTournament> tournaments=new LinkedHashMap<>(); ArenaConnection connection=new ArenaConnection(ArenaConnectionStatus.DISCONNECTED,Optional.empty(),Optional.empty(),Optional.empty(),Instant.now()); public ArenaConnection connection(){return connection;} public void saveConnection(ArenaConnection c){connection=c;} public void saveChallenge(ArenaChallenge c){challenges.put(c.id(),c);} public Optional<ArenaChallenge> findChallenge(String id){return Optional.ofNullable(challenges.get(id));} public List<ArenaChallenge> listChallenges(){return List.copyOf(challenges.values());} public boolean reserveChallenge(String id,int max){return true;} public void saveGame(ArenaGame g){games.put(g.lichessGameId(),g);} public Optional<ArenaGame> findGame(String id){return Optional.ofNullable(games.get(id));} public List<ArenaGame> listActiveGames(){return List.copyOf(games.values());} public void saveTournament(ArenaTournament tournament){tournaments.put(tournament.lichessTournamentId(),tournament);} public Optional<ArenaTournament> findTournament(String id){return Optional.ofNullable(tournaments.get(id));} public List<ArenaTournament> listTournaments(){return List.copyOf(tournaments.values());} }
  private static final class FakeClient implements LichessBotClient { String accepted; Consumer<JsonNode> account; Consumer<Throwable> accountClosed; int accountSubscriptions,tournamentRequests; final Map<String,Integer> gameSubscriptions=new HashMap<>(); final Map<String,Consumer<JsonNode>> gameEvents=new HashMap<>(); final Map<String,Consumer<Throwable>> gameClosed=new HashMap<>(); List<LichessTournamentSnapshot> tournaments=List.of(); RuntimeException tournamentFailure; public StreamHandle streamEvents(String t,Consumer<JsonNode> c,Consumer<Throwable> x){account=c;accountClosed=x;accountSubscriptions++;return ()->{};} public StreamHandle streamGame(String t,String id,Consumer<JsonNode> c,Consumer<Throwable> x){gameSubscriptions.merge(id,1,Integer::sum);gameEvents.put(id,c);gameClosed.put(id,x);return ()->{};} public JsonNode currentGames(String t){return new ObjectMapper().createObjectNode().putArray("nowPlaying");} public List<LichessTournamentSnapshot> currentTournaments(String t){tournamentRequests++;if(tournamentFailure!=null)throw tournamentFailure;return tournaments;} public void acceptChallenge(String t,String id){accepted=id;} public void declineChallenge(String t,String id,String r){} public void sendMove(String t,String g,String u){} public void resign(String t,String g){} public void offerDraw(String t,String g){} }
}
