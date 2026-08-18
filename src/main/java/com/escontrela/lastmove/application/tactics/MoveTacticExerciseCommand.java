package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Moves one exercise by an offset in its suite. */
public record MoveTacticExerciseCommand(
    PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId, int offset) {
  public MoveTacticExerciseCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
  }
}
