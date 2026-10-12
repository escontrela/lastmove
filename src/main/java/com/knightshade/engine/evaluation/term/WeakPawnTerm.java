package com.knightshade.engine.evaluation.term;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.EvaluationAttacks;
import com.knightshade.engine.evaluation.PositionalTerm;

/** Pressure on pawns without pawn support, plus a conservative backward-pawn heuristic. */
public final class WeakPawnTerm implements PositionalTerm {
  private static final int PRESSURE = 8;
  private static final int NO_FUTURE_PAWN_SUPPORT = 4;
  private static final int UNDEFENDED = 8;
  private static final int BACKWARD = 8;

  @Override
  public int evaluate(Position position) {
    var attacks = new EvaluationAttacks();
    attacks.update(position);
    return evaluate(position, attacks);
  }

  public int evaluate(Position position, EvaluationAttacks attacks) {
    int score = 0;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE || Piece.type(piece) != PieceType.PAWN) continue;
      PieceColor color = Piece.color(piece);
      int penalty = weakness(position, attacks, square, color);
      score += color == PieceColor.WHITE ? -penalty : penalty;
    }
    return score;
  }

  private int weakness(Position position, EvaluationAttacks attacks, int square, PieceColor color) {
    long target = 1L << square;
    if ((attacks.pawnAttacks(color) & target) != 0) return 0;
    PieceColor enemy = color.opposite();
    boolean noPawnSupport = (attacks.pawnAttackSpan(color) & target) == 0;
    int penalty = 0;
    // An isolated but unreachable pawn is already covered by PawnStructureTerm.
    if ((attacks.nonPawnAttacks(enemy) & target) != 0) {
      penalty += PRESSURE;
      if (noPawnSupport) penalty += NO_FUTURE_PAWN_SUPPORT;
      if ((attacks.nonPawnAttacks(color) & target) == 0) penalty += UNDEFENDED;
    }
    int front = square + (color == PieceColor.WHITE ? 8 : -8);
    if (noPawnSupport && front >= 8 && front < 56
        && position.pieceAt(front) == Piece.NONE
        && (attacks.pawnAttacks(enemy) & (1L << front)) != 0
        && (attacks.from(square) & attacks.occupancy(enemy)) == 0) {
      // A pawn cannot safely advance, cannot obtain pawn support, and has no capture exit.
      penalty += BACKWARD;
    }
    return penalty;
  }
}
