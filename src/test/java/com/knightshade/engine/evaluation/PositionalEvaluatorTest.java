package com.knightshade.engine.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.evaluation.term.MaterialTerm;
import com.knightshade.engine.evaluation.term.MobilityTerm;
import com.knightshade.engine.evaluation.term.KingSafetyTerm;
import com.knightshade.engine.evaluation.term.DevelopmentTerm;
import com.knightshade.engine.evaluation.term.CenterControlTerm;
import com.knightshade.engine.evaluation.term.PawnStructureTerm;
import com.knightshade.engine.evaluation.term.PassedPawnTerm;
import com.knightshade.engine.evaluation.term.BishopPairTerm;
import com.knightshade.engine.evaluation.term.RookActivityTerm;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PositionalEvaluatorTest {

  private final PositionalEvaluator evaluator = new PositionalEvaluator();

  @Test
  void cachedScoresMatchUncachedTermsAcrossMovesUndoAndDifferentBoards() {
    List<PositionalTerm> terms = List.of(new MaterialTerm(), new MobilityTerm(),
        new KingSafetyTerm(), new DevelopmentTerm(), new CenterControlTerm(),
        new PawnStructureTerm(), new PassedPawnTerm(), new BishopPairTerm(), new RookActivityTerm());
    Random random = new Random(73);
    var generator = new LegalMoveGenerator();
    for (String fen : List.of(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 1",
        "7k/P7/8/8/8/8/7p/K7 w - - 0 1")) {
      var board = FenParser.parse(fen);
      for (int ply = 0; ply < 60; ply++) {
        int parentScore = evaluator.evaluate(board);
        var moves = generator.generate(board);
        for (var move : moves) {
          board.make(move);
          int expected = terms.stream().mapToInt(term -> term.evaluate(board)).sum();
          assertEquals(expected, evaluator.evaluate(board));
          assertEquals(expected, evaluator.evaluate(board));
          board.unmake();
          assertEquals(parentScore, evaluator.evaluate(board));
        }
        if (moves.isEmpty()) {
          break;
        }
        board.make(moves.get(random.nextInt(moves.size())));
      }
    }
  }

  @Test
  void activatesKingInPawnEndgamesInsteadOfKeepingItOnTheBackRank() {
    var passive = FenParser.parse("7k/7p/8/8/8/8/P7/1K6 w - - 0 1");
    var active = FenParser.parse("7k/7p/8/8/3K4/8/P7/8 w - - 0 1");
    assertTrue(evaluator.evaluate(active) > evaluator.evaluate(passive) + 40);
  }

  @Test
  void kingPhaseIsSymmetricAndPromotionsCannotExceedOpeningPhase() {
    var promoted = FenParser.parse("qqqqkqqq/8/8/8/8/8/8/QQQQKQQQ w - - 0 1");
    assertEquals(GamePhase.MAX, GamePhase.of(promoted));
    for (int phase = 0; phase <= GamePhase.MAX; phase++) {
      for (int square = 0; square < 64; square++) {
        assertEquals(PieceSquareTables.kingValue(square, true, phase),
            PieceSquareTables.kingValue(square ^ 56, false, phase));
      }
    }
  }

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
