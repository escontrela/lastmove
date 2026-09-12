package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;

/** A row in a suite's exercise list. */
public record TacticExerciseSummary(
    TacticExerciseId exerciseId,
    String title,
    PieceColor solverColor,
    boolean readyToSolve,
    boolean solved) {

  public TacticExerciseSummary(
      TacticExerciseId exerciseId, String title, PieceColor solverColor, boolean readyToSolve) {
    this(exerciseId, title, solverColor, readyToSolve, false);
  }
}
