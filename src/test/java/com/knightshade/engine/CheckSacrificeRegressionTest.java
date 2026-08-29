package com.knightshade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

/**
 * Regression guards for the engine's handling of checks that hang major pieces: a checking move
 * that allows the opponent to capture a queen must never be preferred over a sound alternative.
 */
class CheckSacrificeRegressionTest {

  private static final Square F3 = Square.of(5, 2);

  private final KnightshadeEngine engine = new KnightshadeEngine();

  private SearchResult search(String fen, int depth) {
    return engine.search(fen, SearchLimits.depth(depth), StopSignal.never());
  }

  @Test
  void refutesAQueenSacrificeCheckByCapturingTheQueen() {
    String fen = "r3k2r/1pQ2p1p/4p1p1/P2p4/3P4/P3Nq2/2R1PP1N/2B3RK w - - 1 24";

    SearchResult result = search(fen, 5);

    assertEquals(F3, result.move().to(), "white must capture the checking queen on f3");
  }

  @Test
  void doesNotSacrificeASafeQueenForACheck() {
    String[] fens = {
      "r3k2r/1pQ2p1p/4p1p1/P2p3q/3P4/P3N3/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p4/3P2q1/P3N3/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p4/3P1q2/P3N3/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p1q2/3P4/P3N3/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4pqp1/P2p4/3P4/P3N3/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p4/3P4/P3N2q/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p4/3P4/P3N1q1/2R1PP1N/2B3RK b - - 1 23",
      "r3k2r/1pQ2p1p/4p1p1/P2p4/3P4/P2qN3/2R1PP1N/2B3RK b - - 1 23"
    };

    for (String fen : fens) {
      SearchResult result = search(fen, 5);
      assertFalse(
          F3.equals(result.move().to()), "the engine must not hang the queen on f3 for a check: " + fen);
    }
  }

  @Test
  void prefersCapturingTheAttackingRookOverAPointlessCheck() {
    String fen = "7k/8/8/7Q/8/8/8/5K1r w - - 0 1";

    SearchResult result = search(fen, 5);

    assertEquals(Square.of(7, 0), result.move().to(), "the engine should capture the rook on h1");
  }

  @Test
  void prefersWinningTheRookOverAPointlessCheck() {
    String fen = "k7/8/8/8/3Q4/8/8/1K1r4 w - - 0 1";

    SearchResult result = search(fen, 5);

    assertEquals(Square.of(3, 0), result.move().to(), "the engine should capture the rook on d1");
  }
}