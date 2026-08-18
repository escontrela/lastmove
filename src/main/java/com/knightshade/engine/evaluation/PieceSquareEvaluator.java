package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;

/**
 * v1 evaluator: material plus piece-square positional bonuses, White minus Black.
 */
public final class PieceSquareEvaluator implements Evaluator {

  @Override
  public int evaluate(Position position) {
    int score = 0;
    for (int index = 0; index < 64; index++) {
      int piece = position.pieceAt(index);
      if (piece == Piece.NONE) {
        continue;
      }
      boolean white = Piece.isWhite(piece);
      int value =
          PieceValues.of(Piece.type(piece)) + PieceSquareTables.value(Piece.type(piece), index, white);
      score += white ? value : -value;
    }
    return score;
  }
}
