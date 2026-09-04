package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.training.memory.MemoryGame;
import com.escontrela.lastmove.domain.training.memory.MemoryGameDifficulty;
import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Immutable application state published to a JavaFX view model. */
public record MemoryGameSnapshot(
    MemoryGameState state,
    int attempt,
    int score,
    int maxPossibleScore,
    Duration remainingTime,
    Duration memorizationRemaining,
    Optional<MemoryGameDifficulty> difficulty,
    Optional<MemoryGameChallenge> challenge,
    boolean showingCompletePosition,
    boolean emptySource,
    List<MemoryGameFeedback> feedback,
    List<MemoryGamePiece> resolvedPieces) {
  public MemoryGameSnapshot {
    state = Objects.requireNonNull(state, "state must not be null");
    remainingTime = Objects.requireNonNull(remainingTime, "remainingTime must not be null");
    memorizationRemaining = Objects.requireNonNull(memorizationRemaining, "memorizationRemaining must not be null");
    difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
    challenge = Objects.requireNonNull(challenge, "challenge must not be null");
    feedback = List.copyOf(Objects.requireNonNull(feedback, "feedback must not be null"));
    resolvedPieces = List.copyOf(Objects.requireNonNull(resolvedPieces, "resolvedPieces must not be null"));
    if (score < 0 || maxPossibleScore < 0 || score > maxPossibleScore) {
      throw new IllegalArgumentException("invalid memory-game score");
    }
  }

  public double successRate() {
    return maxPossibleScore == 0 ? 0.0d : (double) score / maxPossibleScore;
  }

  /** A finished session passes when the success rate reaches the domain threshold. */
  public boolean successful() {
    return successRate() >= MemoryGame.SUCCESS_THRESHOLD;
  }

  /** The result screen offers a single restart only after the first attempt. */
  public boolean canRestart() {
    return state == MemoryGameState.FINISHED && attempt == MemoryGame.FIRST_ATTEMPT;
  }
}
