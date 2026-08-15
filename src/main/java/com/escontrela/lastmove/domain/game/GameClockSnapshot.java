package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable remaining-time state for both players at one point in a progressive game.
 *
 * <p>Both durations are present for a timed game and both are empty for an untimed game. Snapshots
 * are retained alongside played plies so an accepted takeback can restore the exact clock state.
 */
public record GameClockSnapshot(
    Optional<Duration> whiteRemaining, Optional<Duration> blackRemaining) {

  public GameClockSnapshot {
    whiteRemaining = Objects.requireNonNull(whiteRemaining, "whiteRemaining must not be null");
    blackRemaining = Objects.requireNonNull(blackRemaining, "blackRemaining must not be null");
    if (whiteRemaining.isPresent() != blackRemaining.isPresent()) {
      throw new IllegalArgumentException("both clocks must be timed or untimed");
    }
    whiteRemaining.ifPresent(GameClockSnapshot::requireNonNegative);
    blackRemaining.ifPresent(GameClockSnapshot::requireNonNegative);
  }

  /** Creates the initial clock state for an optional time-control declaration. */
  public static GameClockSnapshot initial(Optional<TimeControl> timeControl) {
    Optional<TimeControl> control =
        Objects.requireNonNull(timeControl, "timeControl must not be null");
    Optional<Duration> initial = control.flatMap(TimeControl::initialTime);
    return new GameClockSnapshot(initial, initial);
  }

  /** Returns whether the game uses running clocks. */
  public boolean timed() {
    return whiteRemaining.isPresent();
  }

  /** Returns the remaining time for one side, or empty for an untimed game. */
  public Optional<Duration> remaining(PieceColor color) {
    return Objects.requireNonNull(color, "color must not be null") == PieceColor.WHITE
        ? whiteRemaining
        : blackRemaining;
  }

  /** Applies elapsed thinking time and increment after one accepted move. */
  public GameClockSnapshot afterMove(
      PieceColor mover, Duration elapsed, Duration increment) {
    Objects.requireNonNull(mover, "mover must not be null");
    Duration spent = Objects.requireNonNull(elapsed, "elapsed must not be null");
    Duration added = Objects.requireNonNull(increment, "increment must not be null");
    requireNonNegative(spent);
    requireNonNegative(added);
    if (!timed()) {
      return this;
    }
    Duration remaining = remaining(mover).orElseThrow();
    if (spent.compareTo(remaining) > 0) {
      throw new IllegalArgumentException("elapsed time exceeds the player's remaining time");
    }
    Duration updated = remaining.minus(spent).plus(added);
    return mover == PieceColor.WHITE
        ? new GameClockSnapshot(Optional.of(updated), blackRemaining)
        : new GameClockSnapshot(whiteRemaining, Optional.of(updated));
  }

  /** Returns a clock snapshot with one player's remaining time set to zero. */
  public GameClockSnapshot expired(PieceColor color) {
    PieceColor required = Objects.requireNonNull(color, "color must not be null");
    if (!timed()) {
      throw new IllegalStateException("an untimed game has no clock to expire");
    }
    return required == PieceColor.WHITE
        ? new GameClockSnapshot(Optional.of(Duration.ZERO), blackRemaining)
        : new GameClockSnapshot(whiteRemaining, Optional.of(Duration.ZERO));
  }

  private static void requireNonNegative(Duration duration) {
    if (duration.isNegative()) {
      throw new IllegalArgumentException("clock durations must not be negative");
    }
  }
}
