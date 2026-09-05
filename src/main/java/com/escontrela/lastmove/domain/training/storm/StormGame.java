package com.escontrela.lastmove.domain.training.storm;

import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for one Training Storm attempt.
 *
 * <p>The aggregate records the result of each puzzle occurrence. A failed puzzle remains failed
 * when its solution is eventually completed; only a completed, never-failed puzzle is an acierto.
 * Elapsed time is supplied by the application so this class remains deterministic in tests.
 */
public final class StormGame {

  public static final Duration SESSION_DURATION = Duration.ofMinutes(3);
  public static final double SUCCESS_THRESHOLD_PERCENTAGE = 60.0d;

  private final Map<TacticExerciseId, PuzzleProgress> puzzles = new LinkedHashMap<>();
  private StormGameState state = StormGameState.READY;
  private Duration elapsed = Duration.ZERO;
  private TacticExerciseId activePuzzle;
  private int finalizedPuzzles;
  private int correctAnswers;
  private int failedPuzzles;

  public void start() {
    requireState(StormGameState.READY);
    state = StormGameState.RUNNING;
  }

  /** Activates the next persisted tactic. No puzzle may be activated after expiry. */
  public void activatePuzzle(TacticExerciseId exerciseId) {
    requireState(StormGameState.RUNNING);
    if (activePuzzle != null) {
      throw new IllegalStateException("a puzzle is already active");
    }
    TacticExerciseId required = Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    if (puzzles.containsKey(required)) {
      throw new IllegalArgumentException("the puzzle has already been activated");
    }
    puzzles.put(required, new PuzzleProgress(false, false, false));
    activePuzzle = required;
  }

  /** Permanently marks the active puzzle as failed after the first wrong move. */
  public void markError() {
    markActivePuzzleFailed();
  }

  /** Permanently marks the active puzzle as failed after a hint is requested. */
  public void markHintUsed() {
    PuzzleProgress progress = activeProgress();
    puzzles.put(activePuzzle, progress.withHintUsed());
  }

  /** Completes the active puzzle and counts it exactly once. */
  public void completePuzzle() {
    requireState(StormGameState.RUNNING);
    PuzzleProgress progress = activeProgress();
    puzzles.put(activePuzzle, progress.withSolved());
    finalizedPuzzles = Math.addExact(finalizedPuzzles, 1);
    if (progress.failed()) {
      failedPuzzles = Math.addExact(failedPuzzles, 1);
    } else {
      correctAnswers = Math.addExact(correctAnswers, 1);
    }
    activePuzzle = null;
  }

  /** Updates the global clock and finishes at exactly three minutes. */
  public void updateElapsedTime(Duration elapsed) {
    if (state == StormGameState.READY) {
      throw new IllegalStateException("the session clock cannot advance before start");
    }
    if (state == StormGameState.FINISHED) {
      return;
    }
    Duration required = Objects.requireNonNull(elapsed, "elapsed must not be null");
    if (required.isNegative()) {
      throw new IllegalArgumentException("elapsed must not be negative");
    }
    if (required.compareTo(this.elapsed) < 0) {
      throw new IllegalArgumentException("elapsed must not move backwards");
    }
    this.elapsed = required.compareTo(SESSION_DURATION) >= 0 ? SESSION_DURATION : required;
    if (required.compareTo(SESSION_DURATION) >= 0) {
      activePuzzle = null;
      state = StormGameState.FINISHED;
    }
  }

  private void markActivePuzzleFailed() {
    PuzzleProgress progress = activeProgress();
    if (!progress.failed()) {
      puzzles.put(activePuzzle, progress.withFailed());
    }
  }

  private PuzzleProgress activeProgress() {
    requireState(StormGameState.RUNNING);
    if (activePuzzle == null) {
      throw new IllegalStateException("there is no active puzzle");
    }
    return puzzles.get(activePuzzle);
  }

  private void requireState(StormGameState expected) {
    if (state != expected) {
      throw new IllegalStateException("expected state " + expected + " but was " + state);
    }
  }

  public StormGameState state() { return state; }
  public Optional<TacticExerciseId> activePuzzle() { return Optional.ofNullable(activePuzzle); }
  public int finalizedPuzzles() { return finalizedPuzzles; }
  public int puzzlesFinished() { return finalizedPuzzles; }
  public int correctAnswers() { return correctAnswers; }
  public int hits() { return correctAnswers; }
  public int failedPuzzles() { return failedPuzzles; }
  public int errors() { return failedPuzzles; }
  public Duration elapsedTime() { return elapsed; }
  public Duration remainingTime() { return SESSION_DURATION.minus(elapsed); }
  public double percentage() {
    return finalizedPuzzles == 0 ? 0.0d : (double) correctAnswers / finalizedPuzzles * 100.0d;
  }
  public double successRate() { return percentage() / 100.0d; }
  public boolean isSuccessful() { return percentage() >= SUCCESS_THRESHOLD_PERCENTAGE; }

  public Optional<PuzzleProgress> puzzleProgress(TacticExerciseId exerciseId) {
    return Optional.ofNullable(puzzles.get(Objects.requireNonNull(exerciseId, "exerciseId must not be null")));
  }

  /** Immutable per-puzzle accounting flags. */
  public record PuzzleProgress(boolean failed, boolean hintUsed, boolean solved) {
    private PuzzleProgress withFailed() { return new PuzzleProgress(true, hintUsed, solved); }
    private PuzzleProgress withHintUsed() { return new PuzzleProgress(true, true, solved); }
    private PuzzleProgress withSolved() { return new PuzzleProgress(failed, hintUsed, true); }
  }
}
