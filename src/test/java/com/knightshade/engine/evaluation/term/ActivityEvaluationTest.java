package com.knightshade.engine.evaluation.term;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import org.junit.jupiter.api.Test;

class ActivityEvaluationTest {
  @Test
  void knightMobilityDiscountsEnemyPawnControlledSquares() {
    var term = new MobilityTerm();
    var safe = FenParser.parse("7k/p7/8/8/3N4/8/8/K7 w - - 0 1");
    var restricted = FenParser.parse("7k/8/4p3/8/3N4/8/8/K7 w - - 0 1");
    assertTrue(term.evaluate(safe) > term.evaluate(restricted));
  }

  @Test
  void coordinatedAttackMattersEvenWithTheSamePawnShield() {
    var term = new KingSafetyTerm();
    var remote = FenParser.parse("1r4k1/5ppp/8/q7/8/8/5PPP/5RK1 w - - 0 1");
    var attack = FenParser.parse("6k1/5ppp/8/8/7q/7r/5PPP/5RK1 w - - 0 1");
    assertTrue(term.evaluate(attack) < term.evaluate(remote));
  }

  @Test
  void pawnEndgamesDoNotPenalizeTheActiveKingForMissingShelter() {
    var term = new KingSafetyTerm();
    assertEquals(0, term.evaluate(FenParser.parse("7k/7p/8/8/3K4/8/P7/8 w - - 0 1")));
  }

  @Test
  void rooksPreferOpenThenSemiOpenThenClosedFiles() {
    var term = new RookActivityTerm();
    int open = term.evaluate(FenParser.parse("7k/8/8/8/8/8/8/K2R4 w - - 0 1"));
    int semi = term.evaluate(FenParser.parse("7k/3p4/8/8/8/8/8/K2R4 w - - 0 1"));
    int closed = term.evaluate(FenParser.parse("7k/8/8/8/8/8/3P4/K2R4 w - - 0 1"));
    assertTrue(open > semi && semi > closed);
  }

  @Test
  void seventhRankRookRestrictsKingOnBackRank() {
    var term = new RookActivityTerm();
    int seventh = term.evaluate(FenParser.parse("7k/3R4/8/8/8/8/8/K7 w - - 0 1"));
    int sixth = term.evaluate(FenParser.parse("7k/8/3R4/8/8/8/8/K7 w - - 0 1"));
    assertTrue(seventh > sixth);
  }
}
