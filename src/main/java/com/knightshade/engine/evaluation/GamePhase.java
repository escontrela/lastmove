package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceType;

/** Remaining non-pawn material, clamped so promotions cannot exceed the opening phase. */
public final class GamePhase {
  public static final int MAX = 24;

  private GamePhase() {}

  public static int weight(PieceType type) {
    return switch (type) {
      case KNIGHT, BISHOP -> 1;
      case ROOK -> 2;
      case QUEEN -> 4;
      default -> 0;
    };
  }

  public static int of(Position position) {
    int phase = 0;
    for (int i = 0; i < 64; i++) {
      int piece = position.pieceAt(i);
      if (piece != Piece.NONE) {
        phase += weight(Piece.type(piece));
      }
    }
    return Math.min(MAX, phase);
  }
}
