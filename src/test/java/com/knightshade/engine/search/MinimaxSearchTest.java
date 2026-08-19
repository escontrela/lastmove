package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.MaterialEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import org.junit.jupiter.api.Test;

class MinimaxSearchTest {

  private final MinimaxSearch search =
      new MinimaxSearch(new LegalMoveGenerator(), new MaterialEvaluator());

  @Test
  void findsAMateInOne() {
    String fen = "6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1";

    SearchResult result = search.search(FenParser.parse(fen), SearchLimits.depth(3), StopSignal.never());

    assertNotNull(result.move());
    assertEquals("e1e8", result.move().toUci());
  }

  @Test
  void returnsNullMoveWhenCheckmated() {
    String fen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";

    SearchResult result = search.search(FenParser.parse(fen), SearchLimits.depth(3), StopSignal.never());

    assertNull(result.move());
  }

  @Test
  void returnsALegalMoveFromTheStartingPosition() {
    String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    var board = FenParser.parse(fen);
    var legal = new LegalMoveGenerator().generate(board);

    SearchResult result = search.search(board, SearchLimits.depth(3), StopSignal.never());

    assertNotNull(result.move());
    assertEquals(true, legal.stream().anyMatch(move -> move.equals(result.move())));
  }
}
