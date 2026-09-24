package com.knightshade.engine.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource and depth limits for one search.
 *
 * <p>Iterative search honours both positive limits and stops when either the depth ceiling is
 * completed or the time budget expires.
 */
public record SearchLimits(long maxTimeMillis, int maxDepth) {

  public SearchLimits {
    if (maxTimeMillis < 0) {
      throw new IllegalArgumentException("maxTimeMillis must not be negative");
    }
    if (maxDepth < 0) {
      throw new IllegalArgumentException("maxDepth must not be negative");
    }
  }

  public static SearchLimits timeOnly(Duration time) {
    Objects.requireNonNull(time, "time must not be null");
    return new SearchLimits(Math.max(1L, time.toMillis()), 0);
  }

  public static SearchLimits depth(int depth) {
    return new SearchLimits(0, depth);
  }

  /** Applies whichever of the positive time and depth limits is reached first. */
  public static SearchLimits bounded(Duration time, int depth) {
    Objects.requireNonNull(time, "time must not be null");
    if (depth < 1) throw new IllegalArgumentException("depth must be positive");
    return new SearchLimits(Math.max(1L, time.toMillis()), depth);
  }
}
