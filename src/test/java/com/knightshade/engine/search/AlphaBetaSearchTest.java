package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.PieceSquareEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import org.junit.jupiter.api.Test;

class AlphaBetaSearchTest {

  private final AlphaBetaSearch search =
      new AlphaBetaSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

  @Test
  void findsAMateInOne() {
    SearchResult result =
        search.search(FenParser.parse("6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1"),
            SearchLimits.depth(4), StopSignal.never());

    assertNotNull(result.move());
    assertEquals("e1e8", result.move().toUci());
  }

  @Test
  void returnsNullMoveWhenCheckmated() {
    SearchResult result =
        search.search(FenParser.parse("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"),
            SearchLimits.depth(4), StopSignal.never());

    assertNull(result.move());
  }

  @Test
  void returnsALegalMoveFromTheStartingPosition() {
    String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    var board = FenParser.parse(fen);
    var legal = new LegalMoveGenerator().generate(board);

    SearchResult result = search.search(board, SearchLimits.depth(4), StopSignal.never());

    assertNotNull(result.move());
    assertTrue(legal.stream().anyMatch(move -> move.equals(result.move())));
  }
}
