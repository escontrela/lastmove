package com.knightshade.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KnightshadeEngineTest {

  private static final String STARTING_FEN =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @Test
  void returnsALegalMoveFromFen() {
    KnightshadeEngine engine = new KnightshadeEngine();
    var legal = new LegalMoveGenerator().generate(FenParser.parse(STARTING_FEN));

    SearchResult result =
        engine.search(STARTING_FEN, SearchLimits.timeOnly(Duration.ofMillis(500)), StopSignal.never());

    assertNotNull(result.move());
    assertTrue(legal.stream().anyMatch(move -> move.equals(result.move())));
  }
}
