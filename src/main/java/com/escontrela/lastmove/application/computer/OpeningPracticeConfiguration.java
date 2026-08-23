package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.List;
import java.util.Objects;

/** A provider-neutral opening line and the maximum loss Knightshade may accept to follow it. */
public record OpeningPracticeConfiguration(List<MoveCommand> line, int safetyThresholdCentipawns) {

  public OpeningPracticeConfiguration {
    line = List.copyOf(Objects.requireNonNull(line, "line must not be null"));
    if (line.isEmpty()) {
      throw new IllegalArgumentException("opening practice line must not be empty");
    }
    if (safetyThresholdCentipawns < 0) {
      throw new IllegalArgumentException("safetyThresholdCentipawns must not be negative");
    }
  }
}
