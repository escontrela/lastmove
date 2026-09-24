package com.knightshade.engine.api;

/** The lifecycle point or decision represented by a telemetry sample. */
public enum SearchTelemetryEvent {
  ITERATION_COMPLETED,
  SEARCH_FINISHED,
  /** One admitted speculative task has actually started predicting an opponent reply. */
  PONDER_STARTED,
  /** A completed opponent prediction matched the actual reply (depth may be zero if not reused). */
  PONDER_HIT,
  /** A completed opponent prediction differed from the actual reply. */
  PONDER_MISS
}
