package com.knightshade.engine.time;

/**
 * Wall-clock budget for one search.
 *
 * <p>Iterative deepening consults {@link #exceeded()} after each completed depth to stop cleanly
 * once the allotted thinking time has elapsed.
 */
public final class TimeManager {

  private final long maxTimeMillis;
  private final long startedNanos;

  public TimeManager(long maxTimeMillis) {
    this.maxTimeMillis = maxTimeMillis;
    this.startedNanos = System.nanoTime();
  }

  public boolean exceeded() {
    return maxTimeMillis > 0 && elapsedMillis() >= maxTimeMillis;
  }

  public long elapsedMillis() {
    return (System.nanoTime() - startedNanos) / 1_000_000L;
  }
}
