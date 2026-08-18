package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Position;

/**
 * Butterfly table: quiet moves that produce a cutoff are rewarded with a depth-weighted bonus so
 * they are tried earlier at any ply.
 */
public final class HistoryTable {

  private final int[][] scores = new int[64][64];

  public void record(Move move, int depth) {
    if (move.isCapture() || move.isPromotion()) {
      return;
    }
    int from = Position.indexOf(move.from());
    int to = Position.indexOf(move.to());
    scores[from][to] += depth * depth;
  }

  public int get(Move move) {
    return scores[Position.indexOf(move.from())][Position.indexOf(move.to())];
  }
}
