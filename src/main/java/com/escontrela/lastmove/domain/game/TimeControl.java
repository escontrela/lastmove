package com.escontrela.lastmove.domain.game;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable clock configuration for a progressive chess game.
 *
 * <p>An empty initial time represents an untimed game. Remaining clock values are represented by
 * {@link GameClockSnapshot} so they can be restored independently from this configuration.
 */
public record TimeControl(Optional<Duration> initialTime, Duration increment) {

  public TimeControl {
    initialTime = Objects.requireNonNull(initialTime, "initialTime must not be null");
    increment = Objects.requireNonNull(increment, "increment must not be null");
    initialTime.ifPresent(
        duration -> {
          if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("initialTime must be positive");
          }
        });
    if (increment.isNegative()) {
      throw new IllegalArgumentException("increment must not be negative");
    }
    if (initialTime.isEmpty() && !increment.isZero()) {
      throw new IllegalArgumentException("an untimed game cannot have an increment");
    }
  }

  /** Creates a timed control with the supplied initial duration and increment. */
  public static TimeControl of(Duration initialTime, Duration increment) {
    return new TimeControl(Optional.of(initialTime), increment);
  }

  /** Creates the common fifteen-minutes-plus-ten-seconds control. */
  public static TimeControl fifteenPlusTen() {
    return of(Duration.ofMinutes(15), Duration.ofSeconds(10));
  }

  /** Creates an untimed game configuration. */
  public static TimeControl unlimited() {
    return new TimeControl(Optional.empty(), Duration.ZERO);
  }

  /** Returns whether this configuration uses a running clock. */
  public boolean timed() {
    return initialTime.isPresent();
  }
}
