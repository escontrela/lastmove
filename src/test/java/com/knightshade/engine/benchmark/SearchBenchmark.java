package com.knightshade.engine.benchmark;

import com.knightshade.engine.KnightshadeEngine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.StopSignal;

/** Headless, fixed-depth diagnostic; deliberately not a timing assertion or an Elo test. */
public final class SearchBenchmark {
  private static final String[] POSITIONS = {
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
    "r3k2r/1pQ2p1p/4p1p1/P2p3q/3P4/P3N3/2R1PP1N/2B3RK b - - 1 23",
    "8/5pk1/4p1p1/3pP3/3P1P2/5KP1/8/8 w - - 0 40"
  };

  public static void main(String[] args) {
    int depth = args.length == 0 ? 4 : Integer.parseInt(args[0]);
    SearchLimits limits = args.length > 1
        ? new SearchLimits(Long.parseLong(args[1]), 0) : SearchLimits.depth(depth);
    for (String fen : POSITIONS) {
      new KnightshadeEngine().search(fen, SearchLimits.depth(3), StopSignal.never());
    }
    System.out.println("position,depth,move,score,nodes,millis");
    for (int i = 0; i < POSITIONS.length; i++) {
      var result = new KnightshadeEngine().search(
          POSITIONS[i], limits, StopSignal.never());
      System.out.printf("%d,%d,%s,%d,%d,%d%n", i + 1, result.depth(),
          result.move().toUci(), result.score(), result.nodes(), result.elapsedMillis());
    }
  }
}
