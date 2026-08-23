package com.escontrela.lastmove.domain.statistics;

import java.time.LocalDate;
import java.util.Objects;

/** One inclusive calendar bucket in a game-statistics time series. */
public record GameStatisticsBucket(LocalDate start, long games, GameResultCounts results) {
  public GameStatisticsBucket {
    start = Objects.requireNonNull(start, "start must not be null");
    if (games < 0) throw new IllegalArgumentException("games must be positive");
    results = Objects.requireNonNull(results, "results must not be null");
    if (results.total() != games) throw new IllegalArgumentException("Result counts must add up to games");
  }
  public GameStatisticsBucket(LocalDate start, long games) { this(start, games, new GameResultCounts(0, 0, games)); }
}
