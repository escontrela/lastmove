package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.Objects;
import java.util.Optional;

/** Adds an accepted move to an exercise's solution tree while authoring it. */
public record AppendTacticSolutionMoveCommand(
    PlayerId ownerId,
    TacticSuiteId suiteId,
    TacticExerciseId exerciseId,
    Optional<AnalysisNodeId> parentNodeId,
    MoveCommand move) {
  public AppendTacticSolutionMoveCommand {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    Objects.requireNonNull(parentNodeId, "parentNodeId must not be null");
    Objects.requireNonNull(move, "move must not be null");
  }
}
