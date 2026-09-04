package com.escontrela.lastmove.domain.training.memory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for one three-minute visual-memory training attempt.
 *
 * <p>The aggregate deliberately receives elapsed time instead of reading a clock. Application
 * orchestration can therefore use a production clock while domain tests remain deterministic.
 * An evaluation is applied atomically: every hidden piece increases the possible score and only
 * correctly reconstructed pieces increase the earned score.
 */
public final class MemoryGame {

  public static final Duration SESSION_DURATION = Duration.ofMinutes(3);
  public static final Duration MEMORIZATION_DURATION = Duration.ofSeconds(5);
  public static final double SUCCESS_THRESHOLD = 0.60d;
  public static final int FIRST_ATTEMPT = 1;
  public static final int LAST_ATTEMPT = 2;

  private final int attempt;
  private MemoryGameState state = MemoryGameState.READY;
  private Duration elapsed = Duration.ZERO;
  private MemoryGameDifficulty activeDifficulty;
  private int score;
  private int maxPossibleScore;

  public MemoryGame(int attempt) {
    if (attempt < FIRST_ATTEMPT || attempt > LAST_ATTEMPT) {
      throw new IllegalArgumentException("attempt must be 1 or 2");
    }
    this.attempt = attempt;
  }

  /** Starts the first memorization phase and the session clock. */
  public void start() {
    requireState(MemoryGameState.READY);
    state = MemoryGameState.MEMORIZING;
  }

  /**
   * Opens guessing for the current round and fixes its difficulty from the supplied elapsed time.
   *
   * @return {@code true} when guessing started, or {@code false} when the session expired instead
   */
  public boolean completeMemorization(Duration elapsed) {
    requireState(MemoryGameState.MEMORIZING);
    if (advanceTime(elapsed)) {
      return false;
    }
    activeDifficulty = MemoryGameDifficulty.at(this.elapsed);
    state = MemoryGameState.GUESSING;
    return true;
  }

  /**
   * Evaluates the current round and immediately enters memorization for the next one.
   *
   * <p>An answer arriving at or after the three-minute limit is rejected and does not affect either
   * score. A correctly submitted round can award partial credit.
   *
   * @return {@code true} when the evaluation counted, or {@code false} when time had expired
   */
  public boolean submitEvaluation(int correctlyReconstructedPieces, Duration elapsed) {
    requireState(MemoryGameState.GUESSING);
    int evaluatedPieces = activeDifficulty.hiddenPieceCount();
    if (correctlyReconstructedPieces < 0 || correctlyReconstructedPieces > evaluatedPieces) {
      throw new IllegalArgumentException(
          "correctlyReconstructedPieces must be between 0 and " + evaluatedPieces);
    }
    if (advanceTime(elapsed)) {
      return false;
    }

    int updatedScore = Math.addExact(score, correctlyReconstructedPieces);
    int updatedMaximum = Math.addExact(maxPossibleScore, evaluatedPieces);
    score = updatedScore;
    maxPossibleScore = updatedMaximum;
    activeDifficulty = null;
    state = MemoryGameState.MEMORIZING;
    return true;
  }

  /** Updates the displayed clock and finishes the session when its global limit is reached. */
  public void updateElapsedTime(Duration elapsed) {
    if (state == MemoryGameState.READY) {
      throw new IllegalStateException("the session clock cannot advance before start");
    }
    if (state == MemoryGameState.FINISHED) {
      return;
    }
    advanceTime(elapsed);
  }

  private boolean advanceTime(Duration elapsed) {
    Duration required = Objects.requireNonNull(elapsed, "elapsed must not be null");
    if (required.isNegative()) {
      throw new IllegalArgumentException("elapsed must not be negative");
    }
    if (required.compareTo(this.elapsed) < 0) {
      throw new IllegalArgumentException("elapsed must not move backwards");
    }
    this.elapsed = required.compareTo(SESSION_DURATION) >= 0 ? SESSION_DURATION : required;
    if (required.compareTo(SESSION_DURATION) >= 0) {
      finish();
      return true;
    }
    return false;
  }

  private void finish() {
    activeDifficulty = null;
    state = MemoryGameState.FINISHED;
  }

  private void requireState(MemoryGameState expected) {
    if (state != expected) {
      throw new IllegalStateException("expected state " + expected + " but was " + state);
    }
  }

  public MemoryGameState state() {
    return state;
  }

  public int attempt() {
    return attempt;
  }

  public int score() {
    return score;
  }

  public int maxPossibleScore() {
    return maxPossibleScore;
  }

  public Duration elapsedTime() {
    return elapsed;
  }

  public Duration remainingTime() {
    return SESSION_DURATION.minus(elapsed);
  }

  public Optional<MemoryGameDifficulty> activeDifficulty() {
    return Optional.ofNullable(activeDifficulty);
  }

  /** Returns the earned fraction in the range 0..1, or zero when no round was evaluated. */
  public double successRate() {
    return maxPossibleScore == 0 ? 0.0d : (double) score / maxPossibleScore;
  }

  public boolean isSuccessful() {
    return successRate() >= SUCCESS_THRESHOLD;
  }
}
