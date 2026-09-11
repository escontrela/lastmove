package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/** Geometric attacks for evaluation, including the first occupied square on each ray.
 * These are influence masks, not legal moves (pinned pieces still exert influence).
 */
public final class PieceAttacks {
  private static final long[] KNIGHTS = new long[64];
  private static final long[] KINGS = new long[64];
  private static final long[][] PAWNS = new long[2][64];
  private static final int[][] DIRECTIONS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  static {
    for (int from = 0; from < 64; from++) {
      for (int to = 0; to < 64; to++) {
        int df = Math.abs((to & 7) - (from & 7));
        int dr = (to >>> 3) - (from >>> 3);
        if (df * Math.abs(dr) == 2) KNIGHTS[from] |= 1L << to;
        if (Math.max(df, Math.abs(dr)) == 1) KINGS[from] |= 1L << to;
        if (df == 1 && dr == 1) PAWNS[PieceColor.WHITE.ordinal()][from] |= 1L << to;
        if (df == 1 && dr == -1) PAWNS[PieceColor.BLACK.ordinal()][from] |= 1L << to;
      }
    }
  }

  private PieceAttacks() {}

  public static long pawns(Position position, PieceColor color) {
    long attacks = 0;
    for (int square = 0; square < 64; square++) {
      if (Piece.is(position.pieceAt(square), color, PieceType.PAWN)) {
        attacks |= PAWNS[color.ordinal()][square];
      }
    }
    return attacks;
  }

  public static long from(Position position, int square) {
    int piece = position.pieceAt(square);
    if (piece == Piece.NONE) return 0;
    PieceType type = Piece.type(piece);
    if (type == PieceType.PAWN) return PAWNS[Piece.color(piece).ordinal()][square];
    if (type == PieceType.KNIGHT) return KNIGHTS[square];
    if (type == PieceType.KING) return KINGS[square];
    long attacks = 0;
    int start = type == PieceType.BISHOP ? 4 : 0;
    int end = type == PieceType.ROOK ? 4 : 8;
    for (int d = start; d < end; d++) {
      int file = (square & 7) + DIRECTIONS[d][0];
      int rank = (square >>> 3) + DIRECTIONS[d][1];
      while (file >= 0 && file < 8 && rank >= 0 && rank < 8) {
        int target = rank * 8 + file;
        attacks |= 1L << target;
        if (position.pieceAt(target) != Piece.NONE) break;
        file += DIRECTIONS[d][0];
        rank += DIRECTIONS[d][1];
      }
    }
    return attacks;
  }
}
