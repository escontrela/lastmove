package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.util.Arrays;

/** Reusable influence workspace owned by one evaluator, never shared between workers. */
public final class EvaluationAttacks {
  private final long[] attacks = new long[64];
  private final long[] occupancy = new long[2];
  private final long[] pawnAttacks = new long[2];
  private final long[] nonPawnAttacks = new long[2];
  private final long[] pawnAttackSpans = new long[2];

  public void update(Position position) {
    Arrays.fill(occupancy, 0);
    Arrays.fill(pawnAttacks, 0);
    Arrays.fill(nonPawnAttacks, 0);
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      attacks[square] = PieceAttacks.from(position, square);
      if (piece == Piece.NONE) continue;
      int color = Piece.color(piece).ordinal();
      occupancy[color] |= 1L << square;
      if (Piece.type(piece) == PieceType.PAWN) pawnAttacks[color] |= attacks[square];
      else nonPawnAttacks[color] |= attacks[square];
    }
    long whiteSpan = pawnAttacks[PieceColor.WHITE.ordinal()];
    whiteSpan |= whiteSpan << 8;
    whiteSpan |= whiteSpan << 16;
    whiteSpan |= whiteSpan << 32;
    pawnAttackSpans[PieceColor.WHITE.ordinal()] = whiteSpan;
    long blackSpan = pawnAttacks[PieceColor.BLACK.ordinal()];
    blackSpan |= blackSpan >>> 8;
    blackSpan |= blackSpan >>> 16;
    blackSpan |= blackSpan >>> 32;
    pawnAttackSpans[PieceColor.BLACK.ordinal()] = blackSpan;
  }

  public long from(int square) { return attacks[square]; }

  public long occupancy(PieceColor color) { return occupancy[color.ordinal()]; }

  public long pawnAttacks(PieceColor color) { return pawnAttacks[color.ordinal()]; }

  public long nonPawnAttacks(PieceColor color) { return nonPawnAttacks[color.ordinal()]; }

  public long allAttacks(PieceColor color) {
    return pawnAttacks[color.ordinal()] | nonPawnAttacks[color.ordinal()];
  }

  /** Current and potential pawn attacks after straight advances, conservatively ignoring blockers. */
  public long pawnAttackSpan(PieceColor color) { return pawnAttackSpans[color.ordinal()]; }

  public long mobilityArea(PieceColor color) {
    return ~(occupancy[color.ordinal()] | pawnAttacks[color.opposite().ordinal()]);
  }
}
