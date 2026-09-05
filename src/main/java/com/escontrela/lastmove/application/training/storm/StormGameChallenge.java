package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;

/** Render-ready description of one tactic selected for Storm. */
public record StormGameChallenge(
    PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId,
    String title, PositionSnapshot position, PieceColor solverColor, StormDifficulty difficulty) {
  public StormGameChallenge(
      PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId,
      String title, PositionSnapshot position, PieceColor solverColor) {
    this(ownerId, suiteId, exerciseId, title, position, solverColor, StormDifficulty.EASY);
  }

  public StormGameChallenge {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exerciseId, "exerciseId must not be null");
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
    title = title.trim();
    Objects.requireNonNull(position, "position must not be null");
    Objects.requireNonNull(solverColor, "solverColor must not be null");
    Objects.requireNonNull(difficulty, "difficulty must not be null");
  }
}
