package com.knightshade.engine.evaluation.term;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.EvaluationAttacks;
import com.knightshade.engine.evaluation.PositionalTerm;

/** Pawn-supported minor-piece outposts that opposing pawns cannot challenge by advancing. */
public final class OutpostTerm implements PositionalTerm {
  private static final long WHITE_AREA = (0x7eL << 24) | (0x7eL << 32) | (0x7eL << 40);
  private static final long BLACK_AREA = (0x7eL << 16) | (0x7eL << 24) | (0x7eL << 32);

  @Override
  public int evaluate(Position position) {
    var attacks = new EvaluationAttacks();
    attacks.update(position);
    return evaluate(position, attacks);
  }

  public int evaluate(Position position, EvaluationAttacks attacks) {
    long whiteOutposts = WHITE_AREA & attacks.pawnAttacks(PieceColor.WHITE)
        & ~attacks.pawnAttackSpan(PieceColor.BLACK);
    long blackOutposts = BLACK_AREA & attacks.pawnAttacks(PieceColor.BLACK)
        & ~attacks.pawnAttackSpan(PieceColor.WHITE);
    long empty = ~(attacks.occupancy(PieceColor.WHITE) | attacks.occupancy(PieceColor.BLACK));
    int score = 0;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE) continue;
      PieceType type = Piece.type(piece);
      if (type != PieceType.KNIGHT && type != PieceType.BISHOP) continue;
      PieceColor color = Piece.color(piece);
      long outposts = color == PieceColor.WHITE ? whiteOutposts : blackOutposts;
      int bonus = 0;
      if ((outposts & (1L << square)) != 0) {
        bonus = type == PieceType.KNIGHT ? 24 : 14;
      } else if ((attacks.from(square) & outposts & empty & ~attacks.allAttacks(color.opposite())) != 0) {
        // One small preparation bonus per piece, not per available square.
        bonus = type == PieceType.KNIGHT ? 6 : 4;
      }
      score += color == PieceColor.WHITE ? bonus : -bonus;
    }
    return score;
  }
}
