package com.escontrela.lastmove.application.arena;

/** User-selected rules for one autonomous Knight Shade challenge run. */
public record BotChallengeConfiguration(int clockLimitSeconds, int clockIncrementSeconds,
    String variant, boolean rated, int minimumOpponentRating, int maximumOpponentRating, int maximumGames,
    boolean avoidRepeats, boolean allowRepeatWhenExhausted) {
  public BotChallengeConfiguration {
    if (!validClockLimit(clockLimitSeconds)) throw new IllegalArgumentException("Clock limit must be 15, 30, 45, 60, 90 seconds, or a whole minute up to 180 minutes.");
    if (clockIncrementSeconds < 0 || clockIncrementSeconds > 60) throw new IllegalArgumentException("Clock increment must be between 0 and 60 seconds.");
    if (variant == null || !"standard".equalsIgnoreCase(variant.trim())) throw new IllegalArgumentException("Only standard chess is supported.");
    // Old persisted challenge cycles did not have a lower rating bound. Treat its absent JSON value
    // as the natural minimum so those cycles remain readable after the setting was introduced.
    if (minimumOpponentRating == 0) minimumOpponentRating = 400;
    if (minimumOpponentRating < 400 || minimumOpponentRating > 4000) throw new IllegalArgumentException("Minimum opponent rating must be between 400 and 4000.");
    if (maximumOpponentRating < 400 || maximumOpponentRating > 4000) throw new IllegalArgumentException("Maximum opponent rating must be between 400 and 4000.");
    if (minimumOpponentRating > maximumOpponentRating) throw new IllegalArgumentException("Minimum opponent rating cannot exceed the maximum opponent rating.");
    if (maximumGames < 1 || maximumGames > 100) throw new IllegalArgumentException("Maximum games must be between 1 and 100.");
    variant = "standard";
  }
  /** Compatibility constructor for callers and persisted configurations created before the minimum. */
  public BotChallengeConfiguration(int clockLimitSeconds, int clockIncrementSeconds, String variant,
      boolean rated, int maximumOpponentRating, int maximumGames, boolean avoidRepeats,
      boolean allowRepeatWhenExhausted) {
    this(clockLimitSeconds, clockIncrementSeconds, variant, rated, 400, maximumOpponentRating,
        maximumGames, avoidRepeats, allowRepeatWhenExhausted);
  }

  public static BotChallengeConfiguration defaults() { return new BotChallengeConfiguration(300, 0, "standard", false, 400, 2000, 5, true, false); }

  private static boolean validClockLimit(int seconds) {
    return seconds == 15 || seconds == 30 || seconds == 45 || seconds == 60 || seconds == 90
        || (seconds >= 60 && seconds <= 10_800 && seconds % 60 == 0);
  }
}
