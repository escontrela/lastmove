package com.knightshade.engine.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class PositionalEvaluatorTest {

  private final PositionalEvaluator evaluator = new PositionalEvaluator();

  @Test
  void startingPositionIsSymmetric() {
    var board = FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

    assertEquals(0, evaluator.evaluate(board));
  }

  @Test
  void extraMaterialIsPositiveForWhite() {
    var board = FenParser.parse("7k/8/8/8/8/8/8/K6R w - - 0 1");

    assertTrue(evaluator.evaluate(board) > 0);
  }
}
