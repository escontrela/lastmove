package com.knightshade.engine.search;

/** Reduction in plies for eligible quiet moves; tactical exclusions belong to the caller. */
final class LateMoveReductions {
  private static final int MAX_DEPTH = 64;
  private static final int MAX_MOVES = 256;
  private static final int[][] BASE = new int[MAX_DEPTH + 1][MAX_MOVES + 1];

  static {
    for (int depth = 3; depth <= MAX_DEPTH; depth++) {
      for (int move = 4; move <= MAX_MOVES; move++) {
        BASE[depth][move] = Math.max(1, (int) (Math.log(depth) * Math.log(move) / 2.25));
      }
    }
  }

  private LateMoveReductions() {}

  static int reduction(int depth, int moveNumber, boolean pvNode, int historyScore) {
    if (depth < 3 || moveNumber < 4) return 0;
    int reduction = BASE[Math.min(depth, MAX_DEPTH)][Math.min(moveNumber, MAX_MOVES)];
    // Be less selective in the principal variation and for historically useful quiet moves.
    if (pvNode) reduction--;
    if (historyScore >= 4096) reduction--;
    else if (!pvNode && historyScore <= -4096) reduction++;
    // Always leave a full main-search ply, and bound selectivity pending playing tests.
    return Math.max(0, Math.min(Math.min(4, depth - 2), reduction));
  }
}
