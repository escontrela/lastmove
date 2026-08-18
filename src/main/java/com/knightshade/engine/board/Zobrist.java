package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import java.util.SplittableRandom;

/**
 * Zobrist hash keys for incremental position hashing.
 *
 * <p>Keys are deterministic (fixed seed) so identical positions always hash identically across
 * runs, which keeps the transposition table and tests reproducible.
 */
public final class Zobrist {

  private static final int PIECE_VARIANTS = 12;
  private static final long[][] PIECES = new long[PIECE_VARIANTS][64];
  private static final long[] CASTLING = new long[16];
  private static final long[] EN_PASSANT = new long[9];
  private static final long SIDE_TO_MOVE;

  static {
    SplittableRandom random = new SplittableRandom(0x9E3779B97F4A7C15L);
    for (int variant = 0; variant < PIECE_VARIANTS; variant++) {
      for (int index = 0; index < 64; index++) {
        PIECES[variant][index] = random.nextLong();
      }
    }
    for (int index = 0; index < CASTLING.length; index++) {
      CASTLING[index] = random.nextLong();
    }
    for (int index = 0; index < EN_PASSANT.length; index++) {
      EN_PASSANT[index] = random.nextLong();
    }
    SIDE_TO_MOVE = random.nextLong();
  }

  private Zobrist() {}

  public static long piece(int piece, int index) {
    int variant = (Piece.isWhite(piece) ? 0 : 6) + ((piece & 7) - 1);
    return PIECES[variant][index];
  }

  public static long sideToMove() {
    return SIDE_TO_MOVE;
  }

  public static long castling(CastlingRights rights) {
    int index =
        (rights.whiteKingSide() ? 8 : 0)
            | (rights.whiteQueenSide() ? 4 : 0)
            | (rights.blackKingSide() ? 2 : 0)
            | (rights.blackQueenSide() ? 1 : 0);
    return CASTLING[index];
  }

  public static long enPassant(Square square) {
    return EN_PASSANT[square == null ? 0 : square.getFile() + 1];
  }
}
