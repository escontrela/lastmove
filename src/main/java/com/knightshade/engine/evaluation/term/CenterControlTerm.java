package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;

/** Rewards pawns on the central squares and attacks on them. */
public final class CenterControlTerm implements PositionalTerm {

  private static final int PAWN_ON_CENTER = 15;
  private static final int CENTER_ATTACKED = 8;

  private static final int[] CENTER_SQUARES = {27, 28, 35, 36};

  @Override
  public int evaluate(Position position) {
    int score = 0;
    for (int index : CENTER_SQUARES) {
      int piece = position.pieceAt(index);
      if (piece != Piece.NONE && Piece.type(piece) == PieceType.PAWN) {
        score += Piece.isWhite(piece) ? PAWN_ON_CENTER : -PAWN_ON_CENTER;
      }
      Square square = Position.squareOf(index);
      if (position.isSquareAttacked(square, PieceColor.WHITE)) {
        score += CENTER_ATTACKED;
      }
      if (position.isSquareAttacked(square, PieceColor.BLACK)) {
        score -= CENTER_ATTACKED;
      }
    }
    return score;
  }
}
