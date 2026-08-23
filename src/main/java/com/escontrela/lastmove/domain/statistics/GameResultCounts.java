package com.escontrela.lastmove.domain.statistics;

/** Finished-game outcomes from the selected player's perspective. */
public record GameResultCounts(long won, long lost, long drawn) {
  public GameResultCounts {
    if (won < 0 || lost < 0 || drawn < 0) throw new IllegalArgumentException("Result counts must be positive");
  }
  public long total() { return won + lost + drawn; }
}
