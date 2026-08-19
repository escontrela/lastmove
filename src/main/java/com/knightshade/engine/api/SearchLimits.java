package com.knightshade.engine.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource and depth limits for one search.
 *
 * <p>v0 honours {@code maxDepth} only; {@code maxTimeMillis} is carried through the API and becomes
 * meaningful once iterative deepening and time management arrive in v2.
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
}
