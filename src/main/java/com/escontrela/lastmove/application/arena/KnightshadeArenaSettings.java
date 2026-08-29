package com.escontrela.lastmove.application.arena;

/** Non-secret preferences controlling how Knightshade Arena receives Lichess challenges. */
public record KnightshadeArenaSettings(int maximumConcurrentGames, boolean automaticChallengeAcceptance) {
  public static final int DEFAULT_MAXIMUM_CONCURRENT_GAMES = 1;
  public static final int MAXIMUM_CONCURRENT_GAMES_LIMIT = 16;

  public KnightshadeArenaSettings {
    if (maximumConcurrentGames < 1 || maximumConcurrentGames > MAXIMUM_CONCURRENT_GAMES_LIMIT) {
      throw new IllegalArgumentException(
          "Maximum concurrent games must be between 1 and " + MAXIMUM_CONCURRENT_GAMES_LIMIT);
    }
  }

  public static KnightshadeArenaSettings defaults() {
    return new KnightshadeArenaSettings(DEFAULT_MAXIMUM_CONCURRENT_GAMES, false);
  }
}
