package com.knightshade.engine.evaluation;

import com.escontrela.lastmove.domain.common.PieceType;

/** Shared piece values used by evaluation and move ordering. */
public final class PieceValues {

  public static final int PAWN = 100;
  public static final int KNIGHT = 320;
  public static final int BISHOP = 330;
  public static final int ROOK = 500;
  public static final int QUEEN = 900;

  private PieceValues() {}

  public static int of(PieceType type) {
    return switch (type) {
      case PAWN -> PAWN;
      case KNIGHT -> KNIGHT;
      case BISHOP -> BISHOP;
      case ROOK -> ROOK;
      case QUEEN -> QUEEN;
      case KING -> 0;
    };
  }
}
