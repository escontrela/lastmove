package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards a pawn shield in front of the king. */
public final class KingSafetyTerm implements PositionalTerm {

  @Override
  public int evaluate(Position position) {
    return shield(position, PieceColor.WHITE) - shield(position, PieceColor.BLACK);
  }

  private int shield(Position position, PieceColor color) {
    int kingIndex = -1;
    for (int index = 0; index < 64; index++) {
      if (Piece.is(position.pieceAt(index), color, PieceType.KING)) {
        kingIndex = index;
        break;
      }
    }
    if (kingIndex == -1) {
      return 0;
    }
    int file = kingIndex & 7;
    int rank = kingIndex >>> 3;
    int direction = color == PieceColor.WHITE ? 1 : -1;
    int score = 0;
    for (int fileDelta = -1; fileDelta <= 1; fileDelta++) {
      for (int distance = 1; distance <= 2; distance++) {
        int nextFile = file + fileDelta;
        int nextRank = rank + direction * distance;
        if (nextFile < 0 || nextFile > 7 || nextRank < 0 || nextRank > 7) {
          continue;
        }
        if (Piece.is(position.pieceAt(nextRank * 8 + nextFile), color, PieceType.PAWN)) {
          score += distance == 1 ? 10 : 5;
        }
      }
    }
    return score;
  }
}
