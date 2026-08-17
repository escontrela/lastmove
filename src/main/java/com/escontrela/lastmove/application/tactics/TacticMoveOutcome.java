package com.escontrela.lastmove.application.tactics;

/** Result of one training-board move. */
public record TacticMoveOutcome(TacticWorkspace workspace, boolean accepted) {}
