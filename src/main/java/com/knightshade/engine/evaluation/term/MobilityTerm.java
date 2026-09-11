package com.knightshade.engine.evaluation.term;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.EvaluationAttacks;
import com.knightshade.engine.evaluation.PositionalTerm;
import com.escontrela.lastmove.domain.common.PieceColor;

/** Rewards mobility outside friendly occupancy and enemy pawn control. */
public final class MobilityTerm implements PositionalTerm {
  @Override
  public int evaluate(Position position) {
    var attacks = new EvaluationAttacks();
    attacks.update(position);
    return evaluate(position, attacks);
  }

  public int evaluate(Position position, EvaluationAttacks attacks) {
    return mobility(position, PieceColor.WHITE, attacks) - mobility(position, PieceColor.BLACK, attacks);
  }

  private int mobility(Position position, PieceColor color, EvaluationAttacks attacks) {
    long area = attacks.mobilityArea(color);
    int score = 0;
    for (int square = 0; square < 64; square++) {
      int piece = position.pieceAt(square);
      if (piece == Piece.NONE || Piece.color(piece) != color) continue;
      int weight = switch (Piece.type(piece)) {
        case KNIGHT -> 4;
        case BISHOP -> 3;
        case ROOK -> 2;
        case QUEEN -> 1;
        default -> 0;
      };
      if (weight == 0) continue;
      int baseline = switch (Piece.type(piece)) {
        case KNIGHT -> 4;
        case BISHOP -> 6;
        case ROOK -> 7;
        default -> 14;
      };
      int count = Long.bitCount(attacks.from(square) & area);
      score += (count - baseline) * weight;
    }
    return score;
  }
}
