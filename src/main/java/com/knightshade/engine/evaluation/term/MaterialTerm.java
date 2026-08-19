package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PieceSquareTables;
import com.knightshade.engine.evaluation.PieceValues;
import com.knightshade.engine.evaluation.PositionalTerm;

/** Material plus piece-square positional bonuses, White minus Black. */
public final class MaterialTerm implements PositionalTerm {

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
