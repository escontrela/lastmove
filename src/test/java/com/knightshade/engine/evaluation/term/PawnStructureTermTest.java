package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class PawnStructureTermTest {

  private final PawnStructureTerm term = new PawnStructureTerm();

  @Test
  void penalizesDoubledPawns() {
    var board = FenParser.parse("7k/8/8/8/8/4P3/3PPP2/7K w - - 0 1");

    assertEquals(-20, term.evaluate(board));
  }

  @Test
  void penalizesAnIsolatedPawn() {
    var board = FenParser.parse("7k/8/8/8/4P3/8/8/7K w - - 0 1");

    assertEquals(-15, term.evaluate(board));
  }
}
