package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.PieceSquareEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.ordering.MvvLvaMoveOrderer;
import com.knightshade.engine.see.See;
import org.junit.jupiter.api.Test;

class QuiescenceSearchTest {

  @Test
  void searchesACaptureOfAMajorPieceEvenWhenSeeMarksTheExchangeAsLosing() {
    Board board = FenParser.parse("6k1/2p5/3r4/4Q3/8/8/8/7K w - - 0 1");
    LegalMoveGenerator generator = new LegalMoveGenerator();
    Move queenTakesRook =
        generator.generateCaptures(board).stream()
            .filter(move -> move.toUci().equals("e5d6"))
            .findFirst()
            .orElseThrow();
    assertTrue(See.evaluate(board, queenTakesRook) < 0);

    QuiescenceSearch search =
        new QuiescenceSearch(generator, new PieceSquareEvaluator(), new MvvLvaMoveOrderer());
    search.search(board, -Scores.INF, Scores.INF, 0, StopSignal.never());

    assertTrue(search.nodes() >= 3, "the queen-for-rook line and its recapture must be visited");
  }
}
