package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;

/** Renames one exercise in an owned tactic suite. */
public record RenameTacticExerciseCommand(
    PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId, String title) {
  public RenameTacticExerciseCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    Objects.requireNonNull(title, "title must not be null");
  }
}
