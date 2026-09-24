package com.escontrela.lastmove.application.computer;

import java.time.Duration;
import java.util.Objects;

/** User preferences and bounded budgets for Knightshade anticipatory search. */
public record PonderSettings(
    boolean enabled,
    boolean speculativeWorkerEnabled,
    int predictionDepth,
    Duration predictionBudget,
    int continuationDepth,
    Duration continuationBudget) {

  public static final int DEFAULT_PREDICTION_DEPTH = 4;
  public static final Duration DEFAULT_PREDICTION_BUDGET = Duration.ofMillis(100);
  public static final int DEFAULT_CONTINUATION_DEPTH = 8;
  public static final Duration DEFAULT_CONTINUATION_BUDGET = Duration.ofMillis(1000);

  public PonderSettings {
    predictionBudget = Objects.requireNonNull(predictionBudget, "predictionBudget must not be null");
    continuationBudget = Objects.requireNonNull(continuationBudget, "continuationBudget must not be null");
    if (predictionDepth < 1 || predictionDepth > 32) {
      throw new IllegalArgumentException("predictionDepth must be between 1 and 32");
    }
    if (continuationDepth < 1 || continuationDepth > 64) {
      throw new IllegalArgumentException("continuationDepth must be between 1 and 64");
    }
    validateBudget(predictionBudget, "predictionBudget");
    validateBudget(continuationBudget, "continuationBudget");
  }

  public static PonderSettings defaults() {
    return new PonderSettings(false, true, DEFAULT_PREDICTION_DEPTH,
        DEFAULT_PREDICTION_BUDGET, DEFAULT_CONTINUATION_DEPTH, DEFAULT_CONTINUATION_BUDGET);
  }

  private static void validateBudget(Duration budget, String name) {
    if (budget.isZero() || budget.isNegative() || budget.toMillis() < 1
        || budget.compareTo(Duration.ofMinutes(1)) > 0) {
      throw new IllegalArgumentException(name + " must be positive and at most one minute");
    }
  }
}
