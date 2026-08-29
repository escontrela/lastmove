package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable request asking a computer opponent to choose one move from a complete position. */
public record ComputerMoveRequest(
    PositionSnapshot position,
    Duration maximumThinkingTime,
    List<PositionSnapshot> positionHistory) {

  public ComputerMoveRequest(PositionSnapshot position, Duration maximumThinkingTime) {
    this(position, maximumThinkingTime, List.of());
  }

  public ComputerMoveRequest {
    position = Objects.requireNonNull(position, "position must not be null");
    maximumThinkingTime =
        Objects.requireNonNull(maximumThinkingTime, "maximumThinkingTime must not be null");
    if (maximumThinkingTime.isZero() || maximumThinkingTime.isNegative()) {
      throw new IllegalArgumentException("maximumThinkingTime must be positive");
    }
    positionHistory =
        List.copyOf(Objects.requireNonNull(positionHistory, "positionHistory must not be null"));
  }
}
