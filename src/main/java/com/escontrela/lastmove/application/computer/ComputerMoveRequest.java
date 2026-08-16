package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Duration;
import java.util.Objects;

/** Immutable request asking a computer opponent to choose one move from a complete position. */
public record ComputerMoveRequest(PositionSnapshot position, Duration maximumThinkingTime) {

  public ComputerMoveRequest {
    position = Objects.requireNonNull(position, "position must not be null");
    maximumThinkingTime =
        Objects.requireNonNull(maximumThinkingTime, "maximumThinkingTime must not be null");
    if (maximumThinkingTime.isZero() || maximumThinkingTime.isNegative()) {
      throw new IllegalArgumentException("maximumThinkingTime must be positive");
    }
  }
}
