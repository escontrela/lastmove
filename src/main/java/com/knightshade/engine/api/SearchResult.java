package com.knightshade.engine.api;

import com.knightshade.engine.board.Move;

/**
 * The outcome of one search: the chosen move plus a compact set of diagnostics.
 *
 * <p>{@code move} is {@code null} only when the input position has no legal move (checkmate or
 * stalemate).
 */
public record SearchResult(Move move, int score, int depth, long nodes, long elapsedMillis) {

  private static final int MATE_SCORE = 1_000_000;
  private static final int MAX_PLY = 128;
  private static final int MATE_THRESHOLD = MATE_SCORE - MAX_PLY;

  /** Returns whether the reported score represents a forced mate rather than centipawns. */
  public boolean mate() {
    return Math.abs(score) >= MATE_THRESHOLD;
  }

  /** Returns the number of plies until mate, or zero when the position is already checkmate. */
  public int matePlies() {
    if (!mate()) {
      return 0;
    }
    return Math.max(0, MATE_SCORE - Math.abs(score));
  }
}
