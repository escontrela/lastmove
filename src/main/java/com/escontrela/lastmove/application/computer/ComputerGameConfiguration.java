package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.TimeControl;
import java.time.Duration;
import java.util.Objects;

/** Immutable options selected before starting a human-versus-computer game. */
public record ComputerGameConfiguration(
    String humanName,
    PieceColor humanColor,
    TimeControl timeControl,
    String engineId,
    Duration engineThinkingTime) {

  public ComputerGameConfiguration {
    humanName = requireText(humanName, "humanName");
    humanColor = Objects.requireNonNull(humanColor, "humanColor must not be null");
    timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    engineId = requireText(engineId, "engineId");
    engineThinkingTime =
        Objects.requireNonNull(engineThinkingTime, "engineThinkingTime must not be null");
    if (engineThinkingTime.isZero() || engineThinkingTime.isNegative()) {
      throw new IllegalArgumentException("engineThinkingTime must be positive");
    }
  }

  private static String requireText(String value, String field) {
    String required = Objects.requireNonNull(value, field + " must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return required;
  }
}
