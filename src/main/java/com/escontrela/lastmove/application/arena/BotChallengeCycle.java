package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Minimal durable state needed to resume a bot challenge sequence safely after a restart. */
public record BotChallengeCycle(BotChallengeCycleStatus status, BotChallengeConfiguration configuration,
    List<String> attemptedBotIds, Optional<String> currentBotId, Optional<String> currentChallengeId,
    Optional<String> currentGameId, int completedGames, Optional<String> lastResult,
    Optional<String> stopReason, Instant updatedAt) {
  public BotChallengeCycle {
    status = status == null ? BotChallengeCycleStatus.IDLE : status;
    configuration = configuration == null ? BotChallengeConfiguration.defaults() : configuration;
    attemptedBotIds = attemptedBotIds == null ? List.of() : List.copyOf(attemptedBotIds);
    currentBotId = currentBotId == null ? Optional.empty() : currentBotId;
    currentChallengeId = currentChallengeId == null ? Optional.empty() : currentChallengeId;
    currentGameId = currentGameId == null ? Optional.empty() : currentGameId;
    lastResult = lastResult == null ? Optional.empty() : lastResult;
    stopReason = stopReason == null ? Optional.empty() : stopReason;
    updatedAt = updatedAt == null ? Instant.now() : updatedAt;
  }
  public static BotChallengeCycle idle() { return new BotChallengeCycle(BotChallengeCycleStatus.IDLE, BotChallengeConfiguration.defaults(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), Optional.empty(), Instant.now()); }
  public boolean active() { return switch (status) { case DISCOVERING, CHALLENGING, WAITING_FOR_GAME, PLAYING, STOPPING, ERROR -> true; default -> false; }; }
}
