package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards keeping both bishops. */
public final class BishopPairTerm implements PositionalTerm {

  private static final int BISHOP_PAIR = 40;

  @Override
  public int evaluate(Position position) {
    return pair(position, true) - pair(position, false);
  }

  private int pair(Position position, boolean white) {
    int bishops = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece != Piece.NONE
          && Piece.type(piece) == PieceType.BISHOP
          && Piece.isWhite(piece) == white) {
        bishops++;
      }
    }
    return bishops >= 2 ? BISHOP_PAIR : 0;
  }
}
