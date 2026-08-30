package com.escontrela.lastmove.application.arena;

import java.util.Optional;

/** Result of creating a challenge: Lichess may return a pending challenge or an immediately started game. */
public record LichessChallengeSubmission(Optional<String> challengeId, Optional<String> gameId) {
  public LichessChallengeSubmission {
    challengeId = challengeId == null ? Optional.empty() : challengeId.filter(value -> !value.isBlank());
    gameId = gameId == null ? Optional.empty() : gameId.filter(value -> !value.isBlank());
    if (challengeId.isEmpty() && gameId.isEmpty()) throw new IllegalArgumentException("A Lichess challenge submission needs a challenge or game id.");
  }
  public static LichessChallengeSubmission pending(String challengeId) { return new LichessChallengeSubmission(Optional.of(challengeId), Optional.empty()); }
  public static LichessChallengeSubmission started(String gameId, Optional<String> challengeId) { return new LichessChallengeSubmission(challengeId, Optional.of(gameId)); }
}
