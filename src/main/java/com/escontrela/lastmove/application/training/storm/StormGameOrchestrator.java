package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.application.training.TrainingCancellable;
import com.escontrela.lastmove.application.training.TrainingClock;
import com.escontrela.lastmove.application.training.TrainingUiDispatcher;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.training.storm.StormGame;
import com.escontrela.lastmove.domain.training.storm.StormGameState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Coordinates Storm selection, puzzle feedback, the global three-minute clock and observers. */
@Service
public final class StormGameOrchestrator {
  private static final Duration CLOCK_TICK = Duration.ofSeconds(1);
  /** Lets the solved-line feedback register without interrupting Storm's fast cadence. */
  private static final Duration FEEDBACK_DURATION = Duration.ofMillis(850);
  private final StormGameExerciseSelector selector;
  private final StormGameExerciseSource source;
  private final ChessGameFactory gameFactory;
  private final TrainingClock clock;
  private final TrainingUiDispatcher ui;
  private final List<Consumer<StormGameSnapshot>> observers = new CopyOnWriteArrayList<>();
  private final List<TrainingCancellable> callbacks = new ArrayList<>();
  private TrainingCancellable feedbackCallback;
  private StormGamePuzzleRunner runner;
  private StormGameChallenge challenge;
  private boolean abandoned;
  private boolean emptySource;
  private boolean feedbackPending;

  public StormGameOrchestrator(
      StormGameExerciseSelector selector,
      StormGameExerciseSource source,
      ChessGameFactory gameFactory,
      TrainingClock clock,
      TrainingUiDispatcher ui) {

    this.selector = Objects.requireNonNull(selector, "selector must not be null");
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.ui = Objects.requireNonNull(ui, "ui must not be null");
  }

  public void observe(Consumer<StormGameSnapshot> observer) {

    observers.add(Objects.requireNonNull(observer));
  }

  public void start() {
    ui.dispatch(this::startInternal);
  }

  public void restart() {
    ui.dispatch(
        () -> {
          if (runner != null && runner.snapshot().state() == StormGameState.FINISHED)
            startInternal();
        });
  }

  public void submitMove(MoveCommand move) {

    MoveCommand required = Objects.requireNonNull(move, "move must not be null");
    ui.dispatch(
        () -> {
          if (runner == null
              || feedbackPending
              || runner.snapshot().state() != StormGameState.RUNNING) return;
          StormGameMoveOutcome outcome = runner.attemptMove(required);
          publish(outcome.snapshot());
          if (outcome.feedback().solved()) scheduleNextPuzzle();
        });
  }

  public void requestHint() {
    ui.dispatch(
        () -> {
          if (runner == null
              || feedbackPending
              || runner.snapshot().state() != StormGameState.RUNNING) return;
          publish(runner.requestHint().snapshot());
        });
  }

  public void abandon() {
    ui.dispatch(
        () -> {
          abandoned = true;
          cancelCallbacks();
          clock.cancelAll();
          if (runner != null) runner.close();
        });
  }

  private void startInternal() {

    abandoned = false;
    emptySource = false;
    feedbackPending = false;
    cancelCallbacks();
    clock.cancelAll();
    clock.reset();
    selector.reset();
    runner = new StormGamePuzzleRunner(source, gameFactory, new StormGame());
    Optional<StormGameChallenge> next = selector.next();
    if (next.isEmpty()) {
      emptySource = true;
      challenge = null;
      publish(emptySnapshot());
      return;
    }
    challenge = next.orElseThrow();
    runner.start(challenge);
    publish(runner.snapshot());
    scheduleClock();
    callbacks.add(clock.schedule(StormGame.SESSION_DURATION, () -> ui.dispatch(this::expire)));
  }

  private void scheduleNextPuzzle() {
    feedbackPending = true;
    feedbackCallback = clock.schedule(FEEDBACK_DURATION, () -> ui.dispatch(this::finishFeedback));
    callbacks.add(feedbackCallback);
  }

  private void finishFeedback() {
    feedbackPending = false;
    feedbackCallback = null;
    if (abandoned || runner == null || runner.snapshot().state() == StormGameState.FINISHED) return;
    Optional<StormGameChallenge> next = selector.next();
    if (next.isEmpty()) {
      emptySource = true;
      challenge = null;
      publish(runner.snapshot());
      return;
    }
    challenge = next.orElseThrow();
    runner.start(challenge);
    publish(runner.snapshot());
  }

  private void scheduleClock() {
    callbacks.add(clock.schedule(CLOCK_TICK, () -> ui.dispatch(this::clockTick)));
  }

  private void clockTick() {
    if (abandoned || runner == null || runner.snapshot().state() == StormGameState.FINISHED) return;
    runner.updateElapsedTime(clock.elapsed());
    publish(runner.snapshot());
    if (runner.snapshot().state() != StormGameState.FINISHED) scheduleClock();
    else cancelCallbacks();
  }

  private void expire() {
    if (runner == null || runner.snapshot().state() == StormGameState.FINISHED) return;
    runner.updateElapsedTime(clock.elapsed());
    cancelCallbacks();
    clock.cancelAll();
    publish(runner.snapshot());
  }

  private void publish(StormGameSnapshot snapshot) {

    observers.forEach(observer -> observer.accept(snapshot));
  }

  private StormGameSnapshot emptySnapshot() {
    return new StormGameSnapshot(
        StormGameState.READY, StormGame.SESSION_DURATION, Optional.empty(), 0, 0, 0, 0.0, true);
  }

  private void cancelCallbacks() {
    callbacks.forEach(TrainingCancellable::cancel);
    callbacks.clear();
    if (feedbackCallback != null) {
      feedbackCallback.cancel();
      feedbackCallback = null;
    }
  }
}
