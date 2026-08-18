package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class BishopPairTermTest {

  private final BishopPairTerm term = new BishopPairTerm();

  @Test
  void rewardsKeepingBothBishops() {
    var board = FenParser.parse("b6k/8/8/8/8/8/8/B1B4K w - - 0 1");

    assertEquals(40, term.evaluate(board));
  }

  @Test
  void isNeutralWhenBothSidesKeepThePair() {
    var board = FenParser.parse("bb5k/8/8/8/8/8/8/BB5K w - - 0 1");

    assertEquals(0, term.evaluate(board));
  }
}
