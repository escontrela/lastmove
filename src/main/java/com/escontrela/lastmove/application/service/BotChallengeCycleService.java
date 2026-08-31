package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Durable coordinator for outgoing bot challenges, independent of JavaFX. */
@Service
public final class BotChallengeCycleService {
  private static final Logger log = LoggerFactory.getLogger(BotChallengeCycleService.class);
  static final Duration PENDING_CHALLENGE_TIMEOUT = Duration.ofSeconds(20);
  static final Duration REJECTED_BOT_RETRY_DELAY = Duration.ofSeconds(40);
  static final Duration RATE_LIMIT_RETRY_DELAY = Duration.ofMinutes(1);
  static final int FRIENDLY_BOT_REJECTION_THRESHOLD = 10;

  private final LichessArenaRepository repository;
  private final KnightshadeArenaSettingsRepository settings;
  private final LichessBotClient client;
  private final ApplicationEventPublisher events;
  private final ScheduledExecutorService retryScheduler;
  private final Duration rejectedRetryDelay;
  private final Duration rateLimitRetryDelay;
  private final Set<String> challengedBotIds = ConcurrentHashMap.newKeySet();
  private final Set<String> rejectedBotIds = ConcurrentHashMap.newKeySet();
  private final Set<String> friendlyBotIds = ConcurrentHashMap.newKeySet();
  private final Map<String, String> challengeResults = new ConcurrentHashMap<>();
  private volatile List<LichessBotCandidate> bots = List.of();
  private volatile Optional<String> botError = Optional.empty();
  private ScheduledFuture<?> scheduledRetry;

  @Autowired
  public BotChallengeCycleService(LichessArenaRepository repository,
      KnightshadeArenaSettingsRepository settings, LichessBotClient client,
      ApplicationEventPublisher events) {
    this(repository, settings, client, events, daemonScheduler(),
        REJECTED_BOT_RETRY_DELAY, RATE_LIMIT_RETRY_DELAY);
  }

  BotChallengeCycleService(LichessArenaRepository repository,
      KnightshadeArenaSettingsRepository settings, LichessBotClient client,
      ApplicationEventPublisher events, ScheduledExecutorService scheduler) {
    this(repository, settings, client, events, scheduler,
        REJECTED_BOT_RETRY_DELAY, RATE_LIMIT_RETRY_DELAY);
  }

  BotChallengeCycleService(LichessArenaRepository repository,
      KnightshadeArenaSettingsRepository settings, LichessBotClient client,
      ApplicationEventPublisher events, ScheduledExecutorService scheduler, Duration retryDelay) {
    this(repository, settings, client, events, scheduler, retryDelay, retryDelay);
  }

  BotChallengeCycleService(LichessArenaRepository repository,
      KnightshadeArenaSettingsRepository settings, LichessBotClient client,
      ApplicationEventPublisher events, ScheduledExecutorService scheduler,
      Duration rejectedRetryDelay, Duration rateLimitRetryDelay) {
    this.repository = Objects.requireNonNull(repository);
    this.settings = Objects.requireNonNull(settings);
    this.client = Objects.requireNonNull(client);
    this.events = Objects.requireNonNull(events);
    this.retryScheduler = Objects.requireNonNull(scheduler);
    this.rejectedRetryDelay = Objects.requireNonNull(rejectedRetryDelay);
    this.rateLimitRetryDelay = Objects.requireNonNull(rateLimitRetryDelay);
  }

  public List<LichessBotCandidate> bots() { return bots; }
  public Optional<String> botError() { return botError; }
  public BotChallengeCycle cycle() { return repository.botChallengeCycle(); }
  public Set<String> challengedBotIds() { return Set.copyOf(challengedBotIds); }
  public Set<String> rejectedBotIds() { return Set.copyOf(rejectedBotIds); }
  public Set<String> friendlyBotIds() { return Set.copyOf(friendlyBotIds); }
  public Map<String, String> challengeResults() { return Map.copyOf(challengeResults); }

  public void markBotChallengeSent(String botId) {
    if (blank(botId)) return;
    challengedBotIds.add(botId);
    challengeResults.put(botId, "CHALLENGED");
    publish("bot-challenged:" + botId);
  }

  public void markBotChallengeRejected(String botId) { markBotChallengeRejected(botId, "Rejected"); }

  public void markBotChallengeRejected(String botId, String reason) {
    if (blank(botId)) return;
    challengedBotIds.remove(botId);
    rejectedBotIds.add(botId);
    challengeResults.put(botId, "REJECTED — " + readable(reason, "Rejected"));
    publish("bot-rejected:" + botId);
  }

  public void markBotChallengeAccepted(String botId) {
    if (blank(botId)) return;
    challengedBotIds.remove(botId);
    rememberFriendlyBot(botId);
    challengeResults.put(botId, "ACCEPTED");
    publish("bot-accepted:" + botId);
  }

  public synchronized List<LichessBotCandidate> refreshBots() {
    try {
      bots = List.copyOf(client.onlineBots(token()));
      refreshFriendlyBots();
      botError = Optional.empty();
      publish("bots:" + bots.size());
    } catch (RuntimeException failure) {
      botError = Optional.of(readable(failure.getMessage(), "Could not load online bots."));
      publish(botError.orElseThrow());
    }
    return bots;
  }

  private void refreshFriendlyBots() {
    friendlyBotIds.clear();
    repository.listFriendlyBots().forEach(bot -> friendlyBotIds.add(bot.lichessId()));
  }

  private void rememberFriendlyBot(String botId) {
    LichessBotCandidate candidate = bots.stream()
        .filter(bot -> bot.id().equalsIgnoreCase(botId)).findFirst().orElse(null);
    Instant now = Instant.now();
    repository.saveFriendlyBot(new FriendlyLichessBot(botId,
        candidate == null ? botId : candidate.username(),
        candidate == null ? Optional.empty() : candidate.rating(), now, now));
    friendlyBotIds.add(botId);
  }

  public synchronized BotChallengeCycle start(BotChallengeConfiguration configuration) {
    if (cycle().active()) throw new IllegalStateException("A bot challenge cycle is already active.");
    cancelScheduledRetry();
    challengedBotIds.clear();
    rejectedBotIds.clear();
    challengeResults.clear();
    refreshFriendlyBots();
    save(new BotChallengeCycle(BotChallengeCycleStatus.DISCOVERING, configuration, List.of(),
        Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(),
        Optional.empty(), Instant.now()));
    challengeNext();
    return cycle();
  }

  public synchronized BotChallengeCycle stop() {
    BotChallengeCycle state = cycle();
    if (!state.active()) return state;
    if (state.status() == BotChallengeCycleStatus.STOPPING) return state;
    cancelScheduledRetry();
    if (state.currentGameId().isPresent()) {
      save(copy(state, BotChallengeCycleStatus.STOPPING, state.currentBotId(),
          state.currentChallengeId(), state.currentGameId(), state.completedGames(),
          state.lastResult(), Optional.of("Challenge loop stopped; current game will finish normally.")));
    } else {
      state.currentChallengeId().ifPresent(this::cancelQuietly);
      challengedBotIds.clear();
      save(copy(state, BotChallengeCycleStatus.STOPPED, Optional.empty(), Optional.empty(),
          Optional.empty(), state.completedGames(), state.lastResult(),
          Optional.of("Stopped by user.")));
    }
    return cycle();
  }

  public synchronized void onGameStarted(String gameId, Optional<String> challengeId) {
    onGameStarted(gameId, challengeId, Set.of());
  }

  public synchronized void onGameStarted(String gameId, Optional<String> challengeId,
      Set<String> playerIds) {
    BotChallengeCycle state = cycle();
    if (!state.active()) {
      playerIds.stream().filter(id -> containsIgnoreCase(challengedBotIds, id))
          .findFirst().ifPresent(this::markBotChallengeAccepted);
      return;
    }
    boolean challengeMatches = state.currentChallengeId().isPresent()
        && challengeId.filter(state.currentChallengeId().orElseThrow()::equals).isPresent();
    boolean opponentMatches = state.currentBotId()
        .map(expected -> playerIds.stream().anyMatch(expected::equalsIgnoreCase)).orElse(false);
    boolean gameMatches = state.currentGameId().filter(gameId::equals).isPresent();
    if (!challengeMatches && !opponentMatches && !gameMatches) return;
    cancelScheduledRetry();
    state.currentBotId().ifPresent(this::markBotChallengeAccepted);
    save(copy(state, BotChallengeCycleStatus.PLAYING, state.currentBotId(),
        challengeId.or(state::currentChallengeId), Optional.of(gameId), state.completedGames(),
        state.lastResult(), Optional.empty()));
  }

  public synchronized void onChallengeCanceled(String challengeId, String reason) {
    onChallengeEnded(Optional.of(challengeId), Optional.empty(), reason);
  }

  public synchronized void onChallengeDeclined(Optional<String> challengeId,
      Optional<String> opponentId, String reason) {
    onChallengeEnded(challengeId, opponentId, reason);
  }

  private void onChallengeEnded(Optional<String> challengeId, Optional<String> opponentId,
      String reason) {
    BotChallengeCycle state = cycle();
    if (!state.active()) {
      opponentId.filter(id -> containsIgnoreCase(challengedBotIds, id))
          .ifPresent(id -> markBotChallengeRejected(id, reason));
      return;
    }
    boolean challengeMatches = challengeId.isPresent()
        && state.currentChallengeId().filter(challengeId.orElseThrow()::equals).isPresent();
    boolean opponentMatches = state.currentBotId().isPresent()
        && opponentId.map(candidate -> state.currentBotId().orElseThrow()
            .equalsIgnoreCase(candidate)).orElse(false);
    if (!challengeMatches && !opponentMatches) return;
    String botId = state.currentBotId().orElse("unknown");
    markBotChallengeRejected(botId, reason);
    log.info("Outgoing challenge closed: bot={} challenge={} reason={}", botId,
        state.currentChallengeId().orElse("unknown"), reason);
    if (state.status() == BotChallengeCycleStatus.STOPPING) {
      save(copy(state, BotChallengeCycleStatus.STOPPED, Optional.empty(), Optional.empty(),
          Optional.empty(), state.completedGames(), state.lastResult(),
          Optional.of("Stopped by user.")));
      return;
    }
    waitBeforeNextCandidate(state, reason);
  }

  public synchronized void expireStalePendingChallenge() {
    BotChallengeCycle state = cycle();
    if (state.status() != BotChallengeCycleStatus.WAITING_FOR_GAME
        || state.updatedAt().isAfter(Instant.now().minus(PENDING_CHALLENGE_TIMEOUT))) return;
    String botId = state.currentBotId().orElse("unknown");
    state.currentChallengeId().ifPresent(this::cancelQuietly);
    markBotChallengeRejected(botId, "No response; challenge timed out");
    waitBeforeNextCandidate(state, "No response from " + botId + "; challenge timed out.");
  }

  public synchronized void onGameFinished(String gameId, String result) {
    BotChallengeCycle state = cycle();
    if (state.currentGameId().filter(gameId::equals).isEmpty()) return;
    int completed = state.completedGames() + 1;
    if (state.status() == BotChallengeCycleStatus.STOPPING) {
      save(copy(state, BotChallengeCycleStatus.STOPPED, Optional.empty(), Optional.empty(),
          Optional.empty(), completed, Optional.of(result), Optional.of("Stopped by user.")));
    } else if (completed >= state.configuration().maximumGames()) {
      save(copy(state, BotChallengeCycleStatus.COMPLETED, Optional.empty(), Optional.empty(),
          Optional.empty(), completed, Optional.of(result), Optional.of("Maximum games reached.")));
    } else {
      save(copy(state, BotChallengeCycleStatus.DISCOVERING, Optional.empty(), Optional.empty(),
          Optional.empty(), completed, Optional.of(result), Optional.empty()));
      challengeNext();
    }
  }

  public synchronized void reconcileRemoteState(Set<String> activeGameIds,
      Set<String> outgoingChallengeIds) {
    BotChallengeCycle state = cycle();
    if (!state.active()) return;
    if (state.currentGameId().isPresent()
        && !activeGameIds.contains(state.currentGameId().orElseThrow())) {
      onGameFinished(state.currentGameId().orElseThrow(), "Reconciled after stream interruption");
      return;
    }
    if (state.currentGameId().isEmpty() && state.currentChallengeId().isPresent()
        && !outgoingChallengeIds.contains(state.currentChallengeId().orElseThrow())) {
      onChallengeCanceled(state.currentChallengeId().orElseThrow(),
          "Challenge is no longer pending on Lichess.");
      return;
    }
    recoverErroredCycle(state, activeGameIds);
  }

  public synchronized void reconcileActiveGames(Set<String> activeGameIds) {
    BotChallengeCycle state = cycle();
    if (!state.active()) return;
    if (state.currentGameId().isPresent()
        && !activeGameIds.contains(state.currentGameId().orElseThrow())) {
      onGameFinished(state.currentGameId().orElseThrow(), "Reconciled after stream interruption");
      return;
    }
    recoverErroredCycle(state, activeGameIds);
  }

  /** Rebuilds timers lost on restart and resumes safe transient states. */
  public synchronized void resume() {
    BotChallengeCycle state = cycle();
    if (!state.active() || scheduledRetry != null) return;
    switch (state.status()) {
      case DISCOVERING, CHALLENGING -> challengeNext();
      case WAITING_BETWEEN_CANDIDATES -> scheduleRemaining(state, rejectedRetryDelay, false);
      case WAITING_FOR_RATE_LIMIT -> scheduleRemaining(state,
          rateLimitDelay(state.stopReason().orElse("")), false);
      default -> publish(state.status().name());
    }
  }

  private void challengeNext() {
    BotChallengeCycle state = cycle();
    if (state.status() != BotChallengeCycleStatus.DISCOVERING
        && state.status() != BotChallengeCycleStatus.CHALLENGING) return;
    refreshBots();
    if (botError.isPresent()) {
      waitForTransientFailure(state, botError.orElseThrow());
      return;
    }
    List<LichessBotCandidate> eligible = bots.stream()
        .filter(LichessBotCandidate::available)
        .filter(bot -> !containsIgnoreCase(rejectedBotIds, bot.id()))
        .filter(bot -> bot.rating().map(rating -> rating >= state.configuration().minimumOpponentRating()
            && rating <= state.configuration().maximumOpponentRating()).orElse(false))
        .toList();
    List<LichessBotCandidate> fresh = eligible.stream()
        .filter(bot -> !containsIgnoreCase(state.attemptedBotIds(), bot.id())).toList();
    boolean preferFriendlyBots = rejectedBotIds.size() >= FRIENDLY_BOT_REJECTION_THRESHOLD;
    List<LichessBotCandidate> friendlyFresh = fresh.stream()
        .filter(bot -> containsIgnoreCase(friendlyBotIds, bot.id())).toList();
    List<LichessBotCandidate> friendlyEligible = eligible.stream()
        .filter(bot -> containsIgnoreCase(friendlyBotIds, bot.id())).toList();
    boolean startingNewRound = fresh.isEmpty()
        && state.configuration().allowRepeatWhenExhausted() && !eligible.isEmpty();
    List<LichessBotCandidate> normalPool = !fresh.isEmpty() ? fresh
        : startingNewRound ? eligible : List.of();
    List<LichessBotCandidate> pool = preferFriendlyBots && !friendlyFresh.isEmpty() ? friendlyFresh
        : preferFriendlyBots && !friendlyEligible.isEmpty() ? friendlyEligible : normalPool;
    if (pool.isEmpty()) {
      save(copy(state, BotChallengeCycleStatus.COMPLETED, Optional.empty(), Optional.empty(),
          Optional.empty(), state.completedGames(), state.lastResult(),
          Optional.of("No eligible online bot remains.")));
      return;
    }
    LichessBotCandidate chosen = pool.getFirst();
    List<String> attempted = new ArrayList<>(startingNewRound ? List.of() : state.attemptedBotIds());
    if (!containsIgnoreCase(attempted, chosen.id())) attempted.add(chosen.id());
    BotChallengeCycle challenging = new BotChallengeCycle(BotChallengeCycleStatus.CHALLENGING,
        state.configuration(), attempted, Optional.of(chosen.id()), Optional.empty(),
        Optional.empty(), state.completedGames(), state.lastResult(), Optional.empty(), Instant.now());
    markBotChallengeSent(chosen.id());
    save(challenging);
    log.info("Challenging online bot: bot={} rating={} attempt={}", chosen.username(),
        chosen.rating().map(Object::toString).orElse("unknown"), attempted.size());
    try {
      LichessChallengeSubmission submission = client.challengeBot(token(), chosen.username(),
          state.configuration());
      BotChallengeCycleStatus next = submission.gameId().isPresent()
          ? BotChallengeCycleStatus.PLAYING : BotChallengeCycleStatus.WAITING_FOR_GAME;
      submission.challengeId().ifPresent(id -> logOutgoingChallenge(id, chosen, state.configuration(), null));
      if (submission.gameId().isPresent()) markBotChallengeAccepted(chosen.id());
      save(copy(challenging, next, Optional.of(chosen.id()), submission.challengeId(),
          submission.gameId(), state.completedGames(), state.lastResult(), Optional.empty()));
    } catch (LichessBotChallengeRejectedException rejection) {
      // The adapter uses this exception only for HTTP 400: it is a restriction or cooldown
      // belonging to this opponent, not a global throttle on our account.
      rejectAndContinue(challenging, chosen, rejection.getMessage());
    } catch (RuntimeException failure) {
      if (isRateLimited(failure.getMessage())) waitForRateLimit(challenging, chosen, failure.getMessage());
      else waitForTransientFailure(challenging,
          readable(failure.getMessage(), "Temporary challenge submission failure"));
    }
  }

  private void rejectAndContinue(BotChallengeCycle state, LichessBotCandidate bot, String reason) {
    markBotChallengeRejected(bot.id(), reason);
    waitBeforeNextCandidate(state, reason);
  }

  private void waitBeforeNextCandidate(BotChallengeCycle state, String reason) {
    save(copy(state, BotChallengeCycleStatus.WAITING_BETWEEN_CANDIDATES, Optional.empty(),
        Optional.empty(), Optional.empty(), state.completedGames(), state.lastResult(),
        Optional.of("Waiting " + rejectedRetryDelay.toSeconds()
            + " seconds before next candidate — " + readable(reason, "rejected"))));
    scheduleRetry(rejectedRetryDelay, false);
  }

  private void waitForTransientFailure(BotChallengeCycle state, String reason) {
    challengedBotIds.clear();
    save(copy(state, BotChallengeCycleStatus.WAITING_BETWEEN_CANDIDATES, Optional.empty(),
        Optional.empty(), Optional.empty(), state.completedGames(), state.lastResult(),
        Optional.of("Temporary Lichess error; retrying in " + rejectedRetryDelay.toSeconds()
            + " seconds — " + reason)));
    scheduleRetry(rejectedRetryDelay, false);
  }

  private void waitForRateLimit(BotChallengeCycle state, LichessBotCandidate bot, String response) {
    challengedBotIds.remove(bot.id());
    String detail = readable(response, "Lichess returned a global rate limit");
    Duration delay = rateLimitDelay(detail);
    String message = "Waiting for Lichess rate limiting (" + delay.toSeconds()
        + " seconds) — " + detail;
    log.warn("Global Lichess rate limit detected: bot={} response={}", bot.username(), detail);
    logOutgoingChallenge("bot-rate-limit:" + bot.id() + ":" + Instant.now().toEpochMilli(),
        bot, state.configuration(), message);
    save(copy(state, BotChallengeCycleStatus.WAITING_FOR_RATE_LIMIT, Optional.empty(),
        Optional.empty(), Optional.empty(), state.completedGames(), state.lastResult(),
        Optional.of(message)));
    scheduleRetry(delay, false);
  }

  private void scheduleRemaining(BotChallengeCycle state, Duration delay, boolean resetRound) {
    Duration remaining = delay.minus(Duration.between(state.updatedAt(), Instant.now()));
    scheduleRetry(remaining.isNegative() ? Duration.ZERO : remaining, resetRound);
  }

  private void scheduleRetry(Duration delay, boolean resetRound) {
    cancelScheduledRetry();
    log.info("Bot challenge retry scheduled: retryInSeconds={} resetRound={}", delay.toSeconds(), resetRound);
    scheduledRetry = retryScheduler.schedule(() -> {
      synchronized (this) {
        scheduledRetry = null;
        continueAfterWait(resetRound);
      }
    }, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
  }

  private void continueAfterWait(boolean resetRound) {
    BotChallengeCycle state = cycle();
    if (state.status() != BotChallengeCycleStatus.WAITING_BETWEEN_CANDIDATES
        && state.status() != BotChallengeCycleStatus.WAITING_FOR_RATE_LIMIT) return;
    List<String> attempted = resetRound ? List.of() : state.attemptedBotIds();
    if (resetRound) refreshBots();
    save(new BotChallengeCycle(BotChallengeCycleStatus.DISCOVERING, state.configuration(),
        attempted, Optional.empty(), Optional.empty(), Optional.empty(), state.completedGames(),
        state.lastResult(), Optional.empty(), Instant.now()));
    challengeNext();
  }

  /** Deterministic hook for testing delayed transitions without sleeping. */
  synchronized void triggerScheduledRetryForTest() {
    BotChallengeCycle state = cycle();
    boolean resetRound = false;
    cancelScheduledRetry();
    continueAfterWait(resetRound);
  }

  private void recoverErroredCycle(BotChallengeCycle state, Set<String> activeGameIds) {
    if (state.status() == BotChallengeCycleStatus.ERROR && state.currentGameId().isEmpty()
        && state.currentChallengeId().isEmpty() && activeGameIds.isEmpty())
      waitForTransientFailure(state, "Retrying after Lichess reconciliation");
  }

  private void logOutgoingChallenge(String challengeId, LichessBotCandidate bot,
      BotChallengeConfiguration configuration, String reason) {
    try {
      Instant now = Instant.now();
      repository.saveChallenge(new ArenaChallenge(challengeId, Optional.of(bot.id()), bot.username(),
          bot.rating(), configuration.variant(), configuration.rated(),
          Optional.of(configuration.clockLimitSeconds()), Optional.of(configuration.clockIncrementSeconds()),
          ArenaChallengeDecision.SENT, Optional.ofNullable(reason), now, Optional.empty(), now));
    } catch (RuntimeException failure) {
      log.warn("Could not persist outgoing challenge: bot={} challenge={}", bot.username(), challengeId, failure);
    }
  }

  private BotChallengeCycle copy(BotChallengeCycle old, BotChallengeCycleStatus status,
      Optional<String> bot, Optional<String> challenge, Optional<String> game, int completed,
      Optional<String> result, Optional<String> reason) {
    return new BotChallengeCycle(status, old.configuration(), old.attemptedBotIds(), bot,
        challenge, game, completed, result, reason, Instant.now());
  }

  private void cancelQuietly(String challengeId) {
    try { client.cancelChallenge(token(), challengeId); }
    catch (RuntimeException failure) { log.debug("Could not cancel challenge {}", challengeId, failure); }
  }

  private void cancelScheduledRetry() {
    if (scheduledRetry != null) { scheduledRetry.cancel(false); scheduledRetry = null; }
  }

  private void save(BotChallengeCycle state) {
    repository.saveBotChallengeCycle(state);
    publish(state.status() + " · " + state.completedGames() + "/" + state.configuration().maximumGames());
  }

  private String token() {
    return settings.findBotToken().orElseThrow(() ->
        new IllegalStateException("Configure a Lichess bot token before starting the cycle."));
  }

  private void publish(String detail) {
    events.publishEvent(new LichessArenaEvent(LichessArenaEvent.Type.BOT_CYCLE_UPDATED, "bot-cycle", detail));
  }

  private static boolean containsIgnoreCase(Collection<String> values, String candidate) {
    return values.stream().anyMatch(candidate::equalsIgnoreCase);
  }

  private static boolean isRateLimited(String reason) {
    if (reason == null) return false;
    String normalized = reason.toLowerCase(Locale.ROOT);
    return normalized.contains("rate limit") || normalized.contains("rate-limit")
        || normalized.contains("rate limiting");
  }

  private Duration rateLimitDelay(String response) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern
        .compile("Retry-After:\\s*(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
        .matcher(response == null ? "" : response);
    if (!matcher.find()) return rateLimitRetryDelay;
    try {
      Duration requested = Duration.ofSeconds(Long.parseLong(matcher.group(1)));
      return requested.compareTo(rateLimitRetryDelay) > 0 ? requested : rateLimitRetryDelay;
    } catch (NumberFormatException ignored) {
      return rateLimitRetryDelay;
    }
  }

  private static String readable(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }

  private static ScheduledExecutorService daemonScheduler() {
    return Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "lichess-bot-challenge-retry");
      thread.setDaemon(true);
      return thread;
    });
  }

  @PreDestroy void close() { retryScheduler.shutdownNow(); }
}
