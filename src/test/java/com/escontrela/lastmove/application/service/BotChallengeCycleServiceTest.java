package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.domain.game.GameId;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BotChallengeCycleServiceTest {
  @Test void choosesFreshBotsThenCompletesAtConfiguredLimit() {
    MemoryArena arena = new MemoryArena(); FakeSettings settings = new FakeSettings(); FakeClient client = new FakeClient();
    client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, settings, client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 1600, 2, true, false));
    assertEquals("one", arena.cycle.currentBotId().orElseThrow());
    service.onGameStarted("g1", arena.cycle.currentChallengeId());
    service.onGameFinished("g1", "mate");
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
    service.onGameStarted("g2", arena.cycle.currentChallengeId());
    service.onGameFinished("g2", "resign");
    assertEquals(BotChallengeCycleStatus.COMPLETED, arena.cycle.status());
    assertEquals(2, arena.cycle.completedGames());
    assertEquals(List.of("one", "two"), arena.cycle.attemptedBotIds());
  }

  @Test void skipsBotsOutsideTheConfiguredOpponentRatingRange() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient();
    client.bots = List.of(bot("too-low", 1200), bot("eligible", 1600), bot("too-high", 2200));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 1500, 2000, 2, true, false));

    assertEquals("eligible", arena.cycle.currentBotId().orElseThrow());
  }

  @Test void skipsARejectedBotAndContinuesWithTheNextCandidate() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient();
    client.bots = List.of(bot("daily-limit", 1500), bot("next", 1600));
    client.rejectedBot = "daily-limit";
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.start(new BotChallengeConfiguration(300, 0, "standard", true, 2000, 2, true, false));
    assertEquals(BotChallengeCycleStatus.WAITING_BETWEEN_CANDIDATES, arena.cycle.status());
    service.triggerScheduledRetryForTest();

    assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
    assertEquals("next", arena.cycle.currentBotId().orElseThrow());
    assertEquals(List.of("daily-limit", "next"), arena.cycle.attemptedBotIds());
  }

  @Test void rateLimitedChallengeWaitsAndLeavesAVisibleConsoleMessage() throws Exception {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient();
    client.bots = List.of(bot("rate-limited", 1500), bot("next", 1600));
    client.rateLimitedBot = "rate-limited";
    client.rateLimitedFailures = 1;
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {}, scheduler, java.time.Duration.ZERO);

    try {
      service.start(new BotChallengeConfiguration(300, 0, "standard", true, 2000, 2, true, false));
      service.triggerScheduledRetryForTest();

      assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
      assertEquals("rate-limited", arena.cycle.currentBotId().orElseThrow());
      assertTrue(arena.listChallenges().stream().anyMatch(challenge -> challenge.decisionReason()
          .filter(reason -> reason.equals("Waiting for rate limiting...")).isPresent()));
    } finally {
      service.close();
      scheduler.awaitTermination(1, TimeUnit.SECONDS);
    }
  }

  @Test void stopCancelsPendingChallengeWithoutStartingAnother() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(BotChallengeConfiguration.defaults());
    String id = arena.cycle.currentChallengeId().orElseThrow();
    service.stop();
    assertEquals(id, client.cancelled); assertEquals(BotChallengeCycleStatus.STOPPED, arena.cycle.status());
  }

  @Test void manualChallengeResultChangesFromChallengedToAcceptedOrRejected() {
    MemoryArena arena = new MemoryArena();
    BotChallengeCycleService service = new BotChallengeCycleService(
        arena, new FakeSettings(), new FakeClient(), event -> {});

    service.markBotChallengeSent("accepted-bot");
    service.onGameStarted("game", Optional.empty(), Set.of("accepted-bot"));
    service.markBotChallengeSent("rejected-bot");
    service.onChallengeDeclined(Optional.empty(), Optional.of("rejected-bot"), "Declined: later");

    assertEquals("ACCEPTED", service.challengeResults().get("accepted-bot"));
    assertEquals("REJECTED — Declined: later", service.challengeResults().get("rejected-bot"));
  }

  @Test void repeatModeStartsANewRoundOnlyAfterTryingEveryEligibleBot() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient();
    client.bots = List.of(bot("one", 1400), bot("two", 1500));
    client.immediateGames = true;
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    BotChallengeConfiguration configuration = new BotChallengeConfiguration(
        300, 0, "standard", false, 2000, 4, true, true);

    service.start(configuration);
    String firstGame = arena.cycle.currentGameId().orElseThrow();
    assertEquals("one", arena.cycle.currentBotId().orElseThrow());
    service.onGameFinished(firstGame, "finished");
    String secondGame = arena.cycle.currentGameId().orElseThrow();
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());

    service.onGameFinished(secondGame, "finished");

    assertEquals("one", arena.cycle.currentBotId().orElseThrow());
    assertEquals(List.of("one"), arena.cycle.attemptedBotIds());
  }
  @Test void reconciliationAdvancesWhenThePersistedChallengeIsGoneAndNoGameIsActive() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));

    service.reconcileRemoteState(Set.of(), Set.of());
    service.triggerScheduledRetryForTest();

    assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
    assertEquals(List.of("one", "two"), arena.cycle.attemptedBotIds());
  }
  @Test void immediatelyStartedBotGameIsTrackedAndItsFinishStartsTheNextOpponent() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.immediateGames = true; client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));
    assertEquals(BotChallengeCycleStatus.PLAYING, arena.cycle.status()); assertEquals("g1", arena.cycle.currentGameId().orElseThrow());

    service.onGameFinished("g1", "mate");

    assertEquals("two", arena.cycle.currentBotId().orElseThrow()); assertEquals("g2", arena.cycle.currentGameId().orElseThrow()); assertEquals(1, arena.cycle.completedGames());
  }
  @Test void gameWithoutChallengeIdCorrelatesBySelectedOpponent() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));

    service.onGameStarted("g1", Optional.empty(), Set.of("ONE", "knight-shade-bot"));
    service.onGameFinished("g1", "mate");

    assertEquals(1, arena.cycle.completedGames()); assertEquals("two", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void aGameCreatedDespiteSubmissionErrorRepairsAndContinuesTheCycle() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    arena.cycle = new BotChallengeCycle(BotChallengeCycleStatus.ERROR,
        new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false),
        List.of("one"), Optional.of("one"), Optional.empty(), Optional.empty(), 0,
        Optional.empty(), Optional.of("Lichess returned neither a challenge nor a game id."), Instant.now());
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.onGameStarted("recovered", Optional.empty(), Set.of("one", "knight-shade-bot"));
    service.onGameFinished("recovered", "mate");

    assertEquals(1, arena.cycle.completedGames()); assertEquals("two", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void reconciliationRetriesAnErroredSubmissionWhenLichessHasNoGameOrChallenge() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    arena.cycle = new BotChallengeCycle(BotChallengeCycleStatus.ERROR,
        new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false),
        List.of("one"), Optional.of("one"), Optional.empty(), Optional.empty(), 0,
        Optional.empty(), Optional.of("Temporary transport failure"), Instant.now());
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.reconcileRemoteState(Set.of(), Set.of());
    service.triggerScheduledRetryForTest();

    assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void declinedChallengeAdvancesToTheNextBot() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));
    String id = arena.cycle.currentChallengeId().orElseThrow();

    service.onChallengeDeclined(Optional.of(id), Optional.of("one"), "Declined on Lichess: later");
    service.triggerScheduledRetryForTest();

    assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
    assertEquals(List.of("one", "two"), arena.cycle.attemptedBotIds());
  }
  @Test void declinedChallengeCorrelatesByOpponentWhenSubmissionHadNoId() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.noIds = true; client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));
    assertTrue(arena.cycle.currentChallengeId().isEmpty());

    service.onChallengeDeclined(Optional.empty(), Optional.of("ONE"), "Declined on Lichess");
    service.triggerScheduledRetryForTest();

    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void declinedChallengeForAnotherOpponentIsIgnored() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));

    service.onChallengeDeclined(Optional.of("unknown-challenge"), Optional.of("someone-else"), "Declined on Lichess");

    assertEquals(BotChallengeCycleStatus.WAITING_FOR_GAME, arena.cycle.status());
    assertEquals("one", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void watchdogExpiresOnlyStalePendingChallengesAndRetriesWithTheNextBot() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400), bot("two", 1500));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});
    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));
    String id = arena.cycle.currentChallengeId().orElseThrow();

    service.expireStalePendingChallenge();
    assertEquals("one", arena.cycle.currentBotId().orElseThrow());

    arena.cycle = new BotChallengeCycle(arena.cycle.status(), arena.cycle.configuration(), arena.cycle.attemptedBotIds(),
        arena.cycle.currentBotId(), arena.cycle.currentChallengeId(), arena.cycle.currentGameId(),
        arena.cycle.completedGames(), arena.cycle.lastResult(), arena.cycle.stopReason(), Instant.now().minusSeconds(60));
    service.expireStalePendingChallenge();
    service.triggerScheduledRetryForTest();

    assertEquals(id, client.cancelled);
    assertEquals("two", arena.cycle.currentBotId().orElseThrow());
  }
  @Test void failedBotDiscoveryWaitsAndRetriesInsteadOfDisconnectingTheCycle() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.botsFailure = new IllegalStateException("boom");
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.start(new BotChallengeConfiguration(300, 0, "standard", false, 2000, 2, true, false));

    assertEquals(BotChallengeCycleStatus.WAITING_BETWEEN_CANDIDATES, arena.cycle.status());
  }
  @Test void pendingOutgoingChallengeIsLoggedForTheArenaConsole() {
    MemoryArena arena = new MemoryArena(); FakeClient client = new FakeClient(); client.bots = List.of(bot("one", 1400));
    BotChallengeCycleService service = new BotChallengeCycleService(arena, new FakeSettings(), client, event -> {});

    service.start(new BotChallengeConfiguration(300, 0, "standard", true, 2000, 2, true, false));

    ArenaChallenge logged = arena.findChallenge(arena.cycle.currentChallengeId().orElseThrow()).orElseThrow();
    assertEquals("one", logged.challengerName());
    assertEquals(ArenaChallengeDecision.SENT, logged.decision());
  }
  private static LichessBotCandidate bot(String id, int rating) { return new LichessBotCandidate(id, id, true, true, Optional.of(rating), Optional.empty(), Instant.now()); }
  private static final class FakeSettings implements KnightshadeArenaSettingsRepository { public KnightshadeArenaSettings loadSettings(){return KnightshadeArenaSettings.defaults();} public void saveSettings(KnightshadeArenaSettings s){} public Optional<String> findBotToken(){return Optional.of("token");} public void saveBotToken(String s){} public void deleteBotToken(){} public Optional<LichessBotAccount> findValidatedBotAccount(){return Optional.empty();} public void saveValidatedBotAccount(LichessBotAccount account){} }
  private static final class FakeClient implements LichessBotClient { List<LichessBotCandidate> bots=List.of(); int sequence; String cancelled, rejectedBot, rateLimitedBot; int rateLimitedFailures; boolean immediateGames, noIds; RuntimeException botsFailure; public List<LichessBotCandidate> onlineBots(String token){if(botsFailure!=null)throw botsFailure;return bots;} public LichessChallengeSubmission challengeBot(String token,String username,BotChallengeConfiguration config){if(username.equals(rateLimitedBot)&&rateLimitedFailures-->0)throw new IllegalStateException("Lichess is rate-limiting requests. Wait one minute before retrying.");if(username.equals(rejectedBot))throw new LichessBotChallengeRejectedException("Daily challenge limit reached");int id=++sequence;if(noIds)return new LichessChallengeSubmission(Optional.empty(),Optional.empty());return immediateGames?LichessChallengeSubmission.started("g"+id,Optional.empty()):LichessChallengeSubmission.pending("c"+id);} public void cancelChallenge(String token,String id){cancelled=id;} public StreamHandle streamEvents(String t,Consumer<JsonNode> e,Consumer<Throwable> c){return ()->{};} public StreamHandle streamGame(String t,String i,Consumer<JsonNode> e,Consumer<Throwable> c){return ()->{};} public JsonNode currentGames(String t){return null;} public List<LichessTournamentSnapshot> currentTournaments(String t){return List.of();} public void acceptChallenge(String t,String i){} public void declineChallenge(String t,String i,String r){} public void sendMove(String t,String g,String u){} public void resign(String t,String g){} public void offerDraw(String t,String g){} }
  private static final class MemoryArena implements LichessArenaRepository { BotChallengeCycle cycle=BotChallengeCycle.idle(); final Map<String,ArenaChallenge> challenges=new LinkedHashMap<>(); public BotChallengeCycle botChallengeCycle(){return cycle;} public void saveBotChallengeCycle(BotChallengeCycle value){cycle=value;} public ArenaConnection connection(){return null;} public void saveConnection(ArenaConnection value){} public void saveChallenge(ArenaChallenge value){challenges.put(value.id(),value);} public Optional<ArenaChallenge> findChallenge(String id){return Optional.ofNullable(challenges.get(id));} public List<ArenaChallenge> listChallenges(){return List.copyOf(challenges.values());} public boolean reserveChallenge(String id,int maximum){return false;} public void saveGame(ArenaGame value){} public Optional<ArenaGame> findGame(String id){return Optional.empty();} public List<ArenaGame> listActiveGames(){return List.of();} public void saveTournament(ArenaTournament value){} public Optional<ArenaTournament> findTournament(String id){return Optional.empty();} public List<ArenaTournament> listTournaments(){return List.of();} }
}
