package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class DevelopmentTermTest {

  private final DevelopmentTerm term = new DevelopmentTerm();

  @Test
  void rewardsADevelopedKnightOverOneStillOnItsHomeSquare() {
    var board = FenParser.parse("1n2k3/8/8/8/4N3/8/8/4K3 w - - 0 1");

    assertEquals(10, term.evaluate(board));
  }

  @Test
  void ignoresKnightsAndBishopsStillAtHome() {
    var board = FenParser.parse("1n2k3/8/8/8/8/8/8/4K3 w - - 0 1");

    assertEquals(0, term.evaluate(board));
  }
}
