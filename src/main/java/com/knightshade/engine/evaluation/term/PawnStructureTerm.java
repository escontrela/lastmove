package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceType;

/** Penalizes doubled and isolated pawns. */
public final class PawnStructureTerm implements PositionalTerm {

  private static final int DOUBLED_PENALTY = 20;
  private static final int ISOLATED_PENALTY = 15;

  @Override
  public int evaluate(Position position) {
    return structure(position, true) - structure(position, false);
  }

  private int structure(Position position, boolean white) {
    int[] files = new int[8];
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece != Piece.NONE
          && Piece.type(piece) == PieceType.PAWN
          && Piece.isWhite(piece) == white) {
        files[index & 7]++;
      }
    }
    int score = 0;
    for (int file = 0; file < 8; file++) {
      int count = files[file];
      if (count > 1) {
        score -= (count - 1) * DOUBLED_PENALTY;
      }
      if (count > 0) {
        boolean isolated =
            (file == 0 || files[file - 1] == 0) && (file == 7 || files[file + 1] == 0);
        if (isolated) {
          score -= ISOLATED_PENALTY;
        }
      }
    }
    return score;
  }
}
