package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/** Rewards open/semi-open files and a seventh-rank rook restricting the enemy king. */
public final class RookActivityTerm implements PositionalTerm {
  @Override
  public int evaluate(Position position) {
    int whiteFiles = 0;
    int blackFiles = 0;
    int whiteKingRank = -1;
    int blackKingRank = -1;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE) continue;
      if (Piece.type(piece) == PieceType.PAWN) {
        if (Piece.isWhite(piece)) whiteFiles |= 1 << (square & 7);
        else blackFiles |= 1 << (square & 7);
      } else if (Piece.type(piece) == PieceType.KING) {
        if (Piece.isWhite(piece)) whiteKingRank = square >>> 3;
        else blackKingRank = square >>> 3;
      }
    }
    int score = 0;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE || Piece.type(piece) != PieceType.ROOK) continue;
      boolean white = Piece.color(piece) == PieceColor.WHITE;
      int ownFiles = white ? whiteFiles : blackFiles;
      int enemyFiles = white ? blackFiles : whiteFiles;
      int fileMask = 1 << (square & 7);
      int bonus = (ownFiles & fileMask) != 0 ? 0 : (enemyFiles & fileMask) == 0 ? 24 : 12;
      if (white ? (square >>> 3) == 6 && blackKingRank == 7
          : (square >>> 3) == 1 && whiteKingRank == 0) bonus += 20;
      score += white ? bonus : -bonus;
    }
    return score;
  }
}
