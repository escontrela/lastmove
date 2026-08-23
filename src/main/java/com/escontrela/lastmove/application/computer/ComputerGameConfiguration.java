package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.domain.notation.Fen;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Immutable options selected before starting a human-versus-computer game. */
public record ComputerGameConfiguration(
    String humanName,
    PieceColor humanColor,
    TimeControl timeControl,
    Optional<Fen> startingFen,
    String engineId,
    Duration engineThinkingTime,
    Optional<OpeningPracticeConfiguration> openingPractice) {

  public ComputerGameConfiguration {
    humanName = requireText(humanName, "humanName");
    humanColor = Objects.requireNonNull(humanColor, "humanColor must not be null");
    timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    startingFen = Objects.requireNonNull(startingFen, "startingFen must not be null");
    startingFen.ifPresent(
        fen -> requireText(fen.getValue(), "startingFen"));
    engineId = requireText(engineId, "engineId");
    engineThinkingTime =
        Objects.requireNonNull(engineThinkingTime, "engineThinkingTime must not be null");
    if (engineThinkingTime.isZero() || engineThinkingTime.isNegative()) {
      throw new IllegalArgumentException("engineThinkingTime must be positive");
    }
    openingPractice = Objects.requireNonNull(openingPractice, "openingPractice must not be null");
    if (openingPractice.isPresent() && !ComputerEngineIds.KNIGHTSHADE.equals(engineId)) {
      throw new IllegalArgumentException("opening practice is available only with Knightshade");
    }
  }

  public ComputerGameConfiguration(
      String humanName, PieceColor humanColor, TimeControl timeControl, Optional<Fen> startingFen,
      String engineId, Duration engineThinkingTime) {
    this(humanName, humanColor, timeControl, startingFen, engineId, engineThinkingTime, Optional.empty());
  }

  /** Creates the former default configuration, starting from the normal initial position. */
  public ComputerGameConfiguration(
      String humanName,
      PieceColor humanColor,
      TimeControl timeControl,
      String engineId,
      Duration engineThinkingTime) {
    this(
        humanName,
        humanColor,
        timeControl,
        Optional.empty(),
        engineId,
        engineThinkingTime,
        Optional.empty());
  }

  private static String requireText(String value, String field) {
    String required = Objects.requireNonNull(value, field + " must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return required;
  }
}
