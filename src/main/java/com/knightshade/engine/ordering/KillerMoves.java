package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Move;

/**
 * Two quiet-move slots per ply, populated on beta cutoffs.
 *
 * <p>Killer moves are tactical refutations: a quiet move that caused a cutoff is likely good again
 * at the same ply in sibling positions, so it is tried early.
 */
public final class KillerMoves {

  private static final int MAX_PLY = 128;

  private final Move[][] slots = new Move[MAX_PLY][2];

  public void record(Move move, int ply) {
    if (move.isCapture() || move.isPromotion()) {
      return;
    }
    if (ply < 0 || ply >= MAX_PLY) {
      return;
    }
    if (!move.equals(slots[ply][0])) {
      slots[ply][1] = slots[ply][0];
      slots[ply][0] = move;
    }
  }

  public Move primary(int ply) {
    return ply < 0 || ply >= MAX_PLY ? null : slots[ply][0];
  }

  public Move secondary(int ply) {
    return ply < 0 || ply >= MAX_PLY ? null : slots[ply][1];
  }
}
