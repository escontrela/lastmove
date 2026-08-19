package com.knightshade.engine.api;

/**
 * A cooperative stop signal consulted by the search between nodes.
 *
 * <p>The adapter implements this from a cancellation flag so {@code cancelSearch()} can interrupt
 * a long search without exposing threads to the engine.
 */
@FunctionalInterface
public interface StopSignal {

  boolean shouldStop();

  static StopSignal never() {
    return () -> false;
  }
}
