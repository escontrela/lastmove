package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.training.memory.MemoryGame;
import com.escontrela.lastmove.domain.training.memory.MemoryGameDifficulty;
import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

/** Coordinates the memory-game aggregate, position selection, clock callbacks and UI snapshots. */
@Service
public final class MemoryGameOrchestrator {
  private static final Duration CLOCK_TICK = Duration.ofSeconds(1);
  private static final Duration FEEDBACK_DURATION = Duration.ofSeconds(2);
  private final MemoryGamePositionSelector selector;
  private final MemoryGameClock clock;
  private final MemoryGameUiDispatcher ui;
  private final List<Consumer<MemoryGameSnapshot>> observers = new CopyOnWriteArrayList<>();
  private final List<MemoryGameCancellable> callbacks = new ArrayList<>();
  private MemoryGameCancellable feedbackCallback;
  private MemoryGame game;
  private MemoryGameChallenge challenge;
  private boolean emptySource;
  private boolean abandoned;
  private List<MemoryGameFeedback> feedback = List.of();
  private final List<MemoryGamePiece> resolvedPieces = new ArrayList<>();
  private int correctlyResolved;
  private Duration memorizationStartedAt = Duration.ZERO;

  public MemoryGameOrchestrator(MemoryGamePositionSelector selector, MemoryGameClock clock, MemoryGameUiDispatcher ui) {
    this.selector = Objects.requireNonNull(selector, "selector must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.ui = Objects.requireNonNull(ui, "ui must not be null");
  }

  /** Registers a UI/view-model observer. Registration is safe before or after a session starts. */
  public void observe(Consumer<MemoryGameSnapshot> observer) {
    observers.add(Objects.requireNonNull(observer, "observer must not be null"));
  }

  /** Starts a fresh first attempt; an empty source is published without starting an invalid game. */
  public void start() {
    ui.dispatch(() -> startInternal(1));
  }

  /** Restarts only after attempt one has finished, creating the second and final attempt. */
  public void restart() {
    ui.dispatch(() -> {
      if (game == null || game.state() != MemoryGameState.FINISHED || game.attempt() != 1) return;
      startInternal(2);
    });
  }

  /** Immediately evaluates one reconstructed piece. The round completes after every hidden piece. */
  public void submitPiece(MemoryGamePiece piece) {
    MemoryGamePiece required = Objects.requireNonNull(piece, "piece must not be null");
    ui.dispatch(() -> submitPieceInternal(required));
  }

  /** Abandons the screen and cancels every pending callback. */
  public void abandon() {
    ui.dispatch(() -> {
      abandoned = true;
      cancelCallbacks();
      clock.cancelAll();
    });
  }

  private void startInternal(int attempt) {
    abandoned = false;
    emptySource = false;
    feedback = List.of();
    resolvedPieces.clear();
    correctlyResolved = 0;
    cancelCallbacks();
    clock.cancelAll();
    clock.reset();
    selector.reset();
    game = new MemoryGame(attempt);
    if (!selectNextRound(difficultyAtGuessingTime())) {
      emptySource = true;
      publish();
      return;
    }
    game.start();
    publish();
    scheduleMemorizationEnd();
    scheduleClockTick();
    callbacks.add(clock.schedule(MemoryGame.SESSION_DURATION, () -> ui.dispatch(this::expireSession)));
  }

  private boolean selectNextRound(MemoryGameDifficulty difficulty) {
    Optional<MemoryGameChallenge> next = selector.next(difficulty);
    if (next.isEmpty()) {
      challenge = null;
      cancelCallbacks();
      return false;
    }
    challenge = next.orElseThrow();
    feedback = List.of();
    resolvedPieces.clear();
    correctlyResolved = 0;
    memorizationStartedAt = clock.elapsed();
    return true;
  }

  private MemoryGameDifficulty difficultyAtGuessingTime() {
    return MemoryGameDifficulty.at(clock.elapsed().plus(MemoryGame.MEMORIZATION_DURATION));
  }

  private void scheduleMemorizationEnd() {
    callbacks.add(clock.schedule(MemoryGame.MEMORIZATION_DURATION, () -> ui.dispatch(this::finishMemorization)));
  }

  private void finishMemorization() {
    if (abandoned || game == null || game.state() != MemoryGameState.MEMORIZING) return;
    if (!game.completeMemorization(clock.elapsed())) {
      cancelCallbacks();
      clock.cancelAll();
      publish();
      return;
    }
    if (challenge == null
        || challenge.hiddenPieces().size() < game.activeDifficulty().orElseThrow().hiddenPieceCount()) {
      if (!selectNextRound(game.activeDifficulty().orElseThrow())) {
        emptySource = true;
        publish();
        return;
      }
    }
    publish();
  }

  private void submitPieceInternal(MemoryGamePiece submitted) {
    if (abandoned || game == null || game.state() != MemoryGameState.GUESSING) return;
    if (feedbackCallback != null) {
      feedbackCallback.cancel();
      feedbackCallback = null;
    }
    List<MemoryGamePiece> unresolved = challenge.hiddenPieces().stream()
        .filter(expected -> resolvedPieces.stream().noneMatch(resolved -> resolved.square().equals(expected.square())))
        .toList();
    if (unresolved.isEmpty()) return;
    MemoryGamePiece expected = unresolved.stream()
        .filter(candidate -> candidate.square().equals(submitted.square()))
        .findFirst()
        .orElse(unresolved.getFirst());
    boolean correct = expected.equals(submitted);
    if (correct) correctlyResolved++;
    resolvedPieces.add(expected);
    feedback = List.of(new MemoryGameFeedback(expected.square(), expected, submitted, correct));
    boolean roundComplete = resolvedPieces.size() == challenge.hiddenPieces().size();
    if (roundComplete && !game.submitEvaluation(correctlyResolved, clock.elapsed())) {
      cancelCallbacks();
      clock.cancelAll();
      publish();
      return;
    }
    publish();
    feedbackCallback = clock.schedule(
        FEEDBACK_DURATION, () -> ui.dispatch(() -> finishPieceFeedback(roundComplete)));
    callbacks.add(feedbackCallback);
  }

  private void finishPieceFeedback(boolean roundComplete) {
    if (abandoned || game == null || game.state() == MemoryGameState.FINISHED) return;
    feedbackCallback = null;
    if (clock.elapsed().compareTo(MemoryGame.SESSION_DURATION) >= 0) { expireSession(); return; }
    feedback = List.of();
    if (!roundComplete) {
      publish();
      return;
    }
    if (!selectNextRound(difficultyAtGuessingTime())) { emptySource = true; publish(); return; }
    publish();
    scheduleMemorizationEnd();
  }

  private void expireSession() {
    if (abandoned || game == null || game.state() == MemoryGameState.FINISHED) return;
    game.updateElapsedTime(clock.elapsed());
    cancelCallbacks();
    clock.cancelAll();
    publish();
  }

  private void scheduleClockTick() {
    callbacks.add(clock.schedule(CLOCK_TICK, () -> ui.dispatch(this::clockTick)));
  }

  private void clockTick() {
    if (abandoned || game == null || game.state() == MemoryGameState.FINISHED) return;
    game.updateElapsedTime(clock.elapsed());
    publish();
    if (game.state() != MemoryGameState.FINISHED) scheduleClockTick();
  }

  private void publish() {
    MemoryGameState state = game == null ? MemoryGameState.READY : game.state();
    Duration elapsed = game == null ? Duration.ZERO : game.elapsedTime();
    Duration memorizationRemaining = state == MemoryGameState.MEMORIZING
        ? max(Duration.ZERO, MemoryGame.MEMORIZATION_DURATION.minus(elapsed.minus(memorizationStartedAt)))
        : Duration.ZERO;
    MemoryGameSnapshot snapshot = new MemoryGameSnapshot(
        state,
        game == null ? 1 : game.attempt(),
        game == null ? 0 : game.score(),
        game == null ? 0 : game.maxPossibleScore(),
        game == null ? MemoryGame.SESSION_DURATION : game.remainingTime(),
        memorizationRemaining,
        game == null ? Optional.empty() : game.activeDifficulty(),
        Optional.ofNullable(challenge),
        state == MemoryGameState.MEMORIZING && challenge != null,
        emptySource,
        feedback,
        resolvedPieces);
    observers.forEach(observer -> observer.accept(snapshot));
  }

  private static Duration max(Duration left, Duration right) {
    return left.compareTo(right) >= 0 ? left : right;
  }

  private void cancelCallbacks() {
    callbacks.forEach(MemoryGameCancellable::cancel);
    callbacks.clear();
  }
}
