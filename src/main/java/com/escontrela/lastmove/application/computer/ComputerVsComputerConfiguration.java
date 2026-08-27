package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.TimeControl;
import java.time.Duration;
import java.util.Objects;

/** Options for an ephemeral computer-versus-computer game. */
public record ComputerVsComputerConfiguration(
    String whiteEngineId,
    String blackEngineId,
    TimeControl timeControl,
    Duration thinkingTime,
    Duration moveDelay) {
  public ComputerVsComputerConfiguration {
    whiteEngineId = requireId(whiteEngineId, "whiteEngineId");
    blackEngineId = requireId(blackEngineId, "blackEngineId");
    timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    thinkingTime = Objects.requireNonNull(thinkingTime, "thinkingTime must not be null");
    if (thinkingTime.isZero() || thinkingTime.isNegative()) throw new IllegalArgumentException("thinkingTime must be positive");
    moveDelay = Objects.requireNonNull(moveDelay, "moveDelay must not be null");
    if (moveDelay.isNegative()) throw new IllegalArgumentException("moveDelay must not be negative");
  }

  public ComputerVsComputerConfiguration(
      String whiteEngineId, String blackEngineId, TimeControl timeControl, Duration thinkingTime) {
    this(whiteEngineId, blackEngineId, timeControl, thinkingTime, Duration.ZERO);
  }
  private static String requireId(String value, String field) {
    String id = Objects.requireNonNull(value, field + " must not be null").trim();
    if (id.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
    return id;
  }
}
