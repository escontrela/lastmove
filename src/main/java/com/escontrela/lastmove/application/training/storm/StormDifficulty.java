package com.escontrela.lastmove.application.training.storm;

import java.util.Locale;

/** Difficulty bands used by Storm to choose tactic-suite tags. */
public enum StormDifficulty {
  EASY("easy", "Easy"), MEDIUM("medium", "Medium"), HARD("hard", "Hard");
  private final String tagName;
  private final String displayName;
  StormDifficulty(String tagName, String displayName) { this.tagName = tagName; this.displayName = displayName; }
  public String tagName() { return tagName; }
  public String displayName() { return displayName; }
  public static StormDifficulty forPresentedPuzzles(int presentedPuzzles) {
    if (presentedPuzzles < 10) return EASY;
    if (presentedPuzzles <= 18) return MEDIUM;
    return HARD;
  }
  public static boolean matchesTag(Iterable<String> tags, StormDifficulty difficulty) {
    for (String tag : tags) if (tag != null && tag.trim().toLowerCase(Locale.ROOT).equals(difficulty.tagName)) return true;
    return false;
  }
}
