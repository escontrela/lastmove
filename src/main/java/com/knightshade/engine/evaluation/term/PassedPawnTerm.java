package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards passed pawns, scaled by how far they have advanced. */
public final class PassedPawnTerm implements PositionalTerm {

  private static final int BASE = 10;
  private static final int PER_RANK = 5;

  @Override
  public int evaluate(Position position) {
    return passed(position, true) - passed(position, false);
  }

  private int passed(Position position, boolean white) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE
          || Piece.type(piece) != PieceType.PAWN
          || Piece.isWhite(piece) != white) {
        continue;
      }
      int file = index & 7;
      int rank = index >>> 3;
      if (isPassed(position, file, rank, white)) {
        int advanced = white ? rank : 7 - rank;
        score += BASE + advanced * PER_RANK;
      }
    }
    return score;
  }

  private boolean isPassed(Position position, int file, int rank, boolean white) {
    int direction = white ? 1 : -1;
    for (int nextRank = rank + direction;
        white ? nextRank < 8 : nextRank >= 0;
        nextRank += direction) {
      for (int nextFile = file - 1; nextFile <= file + 1; nextFile++) {
        if (nextFile < 0 || nextFile > 7) {
          continue;
        }
        int piece = position.pieceAt(nextRank * 8 + nextFile);
        if (piece != Piece.NONE
            && Piece.type(piece) == PieceType.PAWN
            && Piece.isWhite(piece) != white) {
          return false;
        }
      }
    }
    return true;
  }
}
