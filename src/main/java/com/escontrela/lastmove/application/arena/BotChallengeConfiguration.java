package com.escontrela.lastmove.application.arena;

/** User-selected rules for one autonomous Knight Shade challenge run. */
public record BotChallengeConfiguration(int clockLimitSeconds, int clockIncrementSeconds,
    String variant, boolean rated, int maximumOpponentRating, int maximumGames,
    boolean avoidRepeats, boolean allowRepeatWhenExhausted) {
  public BotChallengeConfiguration {
    if (clockLimitSeconds < 15) throw new IllegalArgumentException("Clock limit must be at least 15 seconds.");
    if (clockIncrementSeconds < 0) throw new IllegalArgumentException("Clock increment cannot be negative.");
    if (variant == null || !"standard".equalsIgnoreCase(variant.trim())) throw new IllegalArgumentException("Only standard chess is supported.");
    if (maximumOpponentRating < 400 || maximumOpponentRating > 4000) throw new IllegalArgumentException("Opponent rating limit must be between 400 and 4000.");
    if (maximumGames < 1 || maximumGames > 100) throw new IllegalArgumentException("Maximum games must be between 1 and 100.");
    variant = "standard";
  }
  public static BotChallengeConfiguration defaults() { return new BotChallengeConfiguration(300, 0, "standard", false, 2000, 5, true, false); }
}
