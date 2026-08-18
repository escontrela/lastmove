package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class PassedPawnTermTest {

  private final PassedPawnTerm term = new PassedPawnTerm();

  @Test
  void rewardsAPassedPawnByRank() {
    var board = FenParser.parse("7k/8/8/8/4P3/8/8/7K w - - 0 1");

    assertEquals(25, term.evaluate(board));
  }

  @Test
  void doesNotRewardABlockedPawn() {
    var board = FenParser.parse("7k/8/8/4p3/4P3/8/8/7K w - - 0 1");

    assertEquals(0, term.evaluate(board));
  }
}
