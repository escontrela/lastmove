package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;

/** Render-ready state of one tactic attempt or solution-authoring session. */
public record TacticWorkspace(
    TacticSuiteId suiteId,
    String suiteTitle,
    TacticExerciseId exerciseId,
    String exerciseTitle,
    PieceColor solverColor,
    PositionSnapshot position,
    boolean readyToSolve,
    boolean solved,
    int attemptedMoves,
    int correctMoves,
    int hintCount,
    int accuracyPercentage,
    String status) {}
