package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LateMoveReductionsTest {
  @Test
  void leavesEarlyMovesAndShallowSearchesAtFullDepth() {
    assertEquals(0, LateMoveReductions.reduction(2, 30, false, -16000));
    assertEquals(0, LateMoveReductions.reduction(12, 3, false, -16000));
  }

  @Test
  void spendsLessDepthOnLateMovesInDeeperSearches() {
    int early = LateMoveReductions.reduction(8, 4, false, 0);
    int late = LateMoveReductions.reduction(8, 24, false, 0);
    assertTrue(late > 1);
    assertTrue(late > early);
    assertTrue(late > LateMoveReductions.reduction(3, 24, false, 0));
  }

  @Test
  void protectsPrincipalVariationAndHistoricallyUsefulMoves() {
    int ordinary = LateMoveReductions.reduction(8, 16, false, 0);
    assertTrue(LateMoveReductions.reduction(8, 16, true, 0) < ordinary);
    assertTrue(LateMoveReductions.reduction(8, 16, false, 8000) < ordinary);
    assertTrue(LateMoveReductions.reduction(8, 16, false, -8000) > ordinary);
  }

  @Test
  void neverReducesPastTheMainSearchOrExceedsFourPlies() {
    for (int depth = 3; depth <= 128; depth++) {
      for (int move = 4; move <= 300; move++) {
        int reduction = LateMoveReductions.reduction(depth, move, false, -16000);
        assertTrue(reduction >= 0 && reduction <= 4);
        assertTrue(depth - 1 - reduction >= 1);
      }
    }
  }
}
