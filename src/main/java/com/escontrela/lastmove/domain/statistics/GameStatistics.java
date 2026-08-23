package com.escontrela.lastmove.domain.statistics;

import java.util.List;
import java.util.Objects;

/** Immutable aggregate rendered by the game-statistics screen. */
public record GameStatistics(List<GameStatisticsBucket> buckets, GameResultCounts results) {
  public GameStatistics {
    buckets = List.copyOf(Objects.requireNonNull(buckets, "buckets must not be null"));
    results = Objects.requireNonNull(results, "results must not be null");
  }
}
