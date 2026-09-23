package com.knightshade.engine.api;

/** Optional observer for completed iterative-deepening snapshots. */
@FunctionalInterface
public interface SearchTelemetryListener {
  SearchTelemetryListener NONE = snapshot -> {};

  /** Receives a snapshot only after a complete depth or the final search result. */
  void onSnapshot(SearchTelemetrySnapshot snapshot);
}
