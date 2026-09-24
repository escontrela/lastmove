package com.knightshade.engine.api;

/** Optional observer for completed iterative-deepening snapshots. */
@FunctionalInterface
public interface SearchTelemetryListener {
  SearchTelemetryListener NONE = snapshot -> {};

  /** Receives snapshots after a complete depth, final search result, or validated ponder decision. */
  void onSnapshot(SearchTelemetrySnapshot snapshot);
}
