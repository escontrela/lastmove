package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceType;

/**
 * v0 evaluator: the sum of material values, White minus Black.
 *
 * <p>Piece-square tables, mobility and king safety are added in later versions; this evaluator
 * exists to make the minimax search play sensibly and to anchor the {@link Evaluator} contract.
 */
public final class MaterialEvaluator implements Evaluator {

  @Override
  public int evaluate(Position position) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE) {
        continue;
      }
      int value = value(Piece.type(piece));
      score += Piece.isWhite(piece) ? value : -value;
    }
    return score;
  }

  private int value(PieceType type) {
    return switch (type) {
      case PAWN -> 100;
      case KNIGHT -> 320;
      case BISHOP -> 330;
      case ROOK -> 500;
      case QUEEN -> 900;
      case KING -> 0;
    };
  }
}
