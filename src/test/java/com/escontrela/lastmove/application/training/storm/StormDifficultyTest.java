package com.escontrela.lastmove.application.training.storm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StormDifficultyTest {
  @Test
  void changesAfterTenPresentedPuzzlesAndBecomesHardAfterEighteen() {
    assertEquals(StormDifficulty.EASY, StormDifficulty.forPresentedPuzzles(0));
    assertEquals(StormDifficulty.EASY, StormDifficulty.forPresentedPuzzles(9));
    assertEquals(StormDifficulty.MEDIUM, StormDifficulty.forPresentedPuzzles(10));
    assertEquals(StormDifficulty.MEDIUM, StormDifficulty.forPresentedPuzzles(18));
    assertEquals(StormDifficulty.HARD, StormDifficulty.forPresentedPuzzles(19));
  }
}
