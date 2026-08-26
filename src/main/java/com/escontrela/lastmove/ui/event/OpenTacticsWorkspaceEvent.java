package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;
import java.util.Optional;

/** Requests opening one tactic suite, optionally focused on an exercise in authoring mode. */
public record OpenTacticsWorkspaceEvent(
    TacticSuiteId suiteId, Optional<TacticExerciseId> exerciseId, boolean authoring) {
  public OpenTacticsWorkspaceEvent {
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
  }
}