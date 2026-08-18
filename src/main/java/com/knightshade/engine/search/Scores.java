package com.knightshade.engine.search;

/** Score constants and mate-distance helpers shared by the search implementations. */
final class Scores {

  static final int MATE = 1_000_000;
  static final int INF = MATE + 1_000;
  static final int MAX_PLY = 128;
  static final int MATE_THRESHOLD = MATE - MAX_PLY;

  private Scores() {}

  static boolean isMate(int score) {
    return Math.abs(score) >= MATE_THRESHOLD;
  }

  /** Converts a node-local mate score to a distance-from-root score for the transposition table. */
  static int toTable(int score, int ply) {
    if (isMate(score)) {
      return score > 0 ? score + ply : score - ply;
    }
    return score;
  }

  /** Converts a transposition-table mate score back to the current node's frame. */
  static int fromTable(int score, int ply) {
    if (isMate(score)) {
      return score > 0 ? score - ply : score + ply;
    }
    return score;
  }
}
