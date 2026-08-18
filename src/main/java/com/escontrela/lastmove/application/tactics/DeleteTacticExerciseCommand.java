package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Removes one exercise from an owned tactic suite. */
public record DeleteTacticExerciseCommand(
    PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId) {
  public DeleteTacticExerciseCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
  }
}
