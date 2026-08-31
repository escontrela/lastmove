package com.escontrela.lastmove.domain.game;

/** Domain reason that made a progressive chess game final. */
public enum GameTerminationReason {
  CHECKMATE,
  STALEMATE,
  THREEFOLD_REPETITION,
  DRAW_AGREEMENT,
  INSUFFICIENT_MATERIAL,
  FIFTY_MOVE_RULE,
  RESIGNATION,
  TIMEOUT
}
