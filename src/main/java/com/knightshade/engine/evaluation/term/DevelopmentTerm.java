package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards minor pieces that have left their home squares (opening development). */
public final class DevelopmentTerm implements PositionalTerm {

  private static final int DEVELOPED_MINOR = 10;

  @Override
  public int evaluate(Position position) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE) {
        continue;
      }
      PieceType type = Piece.type(piece);
      if (type != PieceType.KNIGHT && type != PieceType.BISHOP) {
        continue;
      }
      boolean white = Piece.isWhite(piece);
      if (!isHomeSquare(index, white, type)) {
        score += white ? DEVELOPED_MINOR : -DEVELOPED_MINOR;
      }
    }
    return score;
  }

  private boolean isHomeSquare(int index, boolean white, PieceType type) {
    int rank = index >>> 3;
    int file = index & 7;
    int homeRank = white ? 0 : 7;
    if (rank != homeRank) {
      return false;
    }
    if (type == PieceType.KNIGHT) {
      return file == 1 || file == 6;
    }
    return file == 2 || file == 5;
  }
}
