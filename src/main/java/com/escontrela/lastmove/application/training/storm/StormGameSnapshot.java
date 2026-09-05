package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.domain.training.storm.StormGame;
import com.escontrela.lastmove.domain.training.storm.StormGameState;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;

/** Immutable Storm state published to the UI. */
public record StormGameSnapshot(
    StormGameState state,
    Duration remainingTime,
    Optional<StormGameChallenge> challenge,
    int finalizedPuzzles,
    int correctAnswers,
    int failedPuzzles,
    double percentage,
    boolean emptySource,
    Optional<TacticWorkspace> workspace,
    Optional<StormGameFeedback> feedback) {
  public StormGameSnapshot(StormGameState state, Duration remainingTime, Optional<StormGameChallenge> challenge,
      int finalizedPuzzles, int correctAnswers, int failedPuzzles, double percentage, boolean emptySource) {
    this(state, remainingTime, challenge, finalizedPuzzles, correctAnswers, failedPuzzles, percentage,
        emptySource, Optional.empty(), Optional.empty());
  }
  public StormGameSnapshot {
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(remainingTime, "remainingTime must not be null");
    challenge = Objects.requireNonNull(challenge, "challenge must not be null");
    workspace = Objects.requireNonNull(workspace, "workspace must not be null");
    feedback = Objects.requireNonNull(feedback, "feedback must not be null");
    if (remainingTime.isNegative() || finalizedPuzzles < 0 || correctAnswers < 0 || failedPuzzles < 0
        || correctAnswers + failedPuzzles > finalizedPuzzles || percentage < 0.0d || percentage > 100.0d) {
      throw new IllegalArgumentException("invalid storm snapshot");
    }
    if (state == StormGameState.RUNNING && !emptySource && challenge.isEmpty()) {
      throw new IllegalArgumentException("a running Storm session requires an active challenge");
    }
  }

  public boolean successful() { return percentage >= StormGame.SUCCESS_THRESHOLD_PERCENTAGE; }
  public double successRate() { return percentage / 100.0d; }
}
