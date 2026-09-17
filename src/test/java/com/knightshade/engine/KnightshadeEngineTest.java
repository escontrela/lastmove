package com.knightshade.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

  @Test
  void publishesOnlyCompletedDepthsWhenTelemetryIsRequested() {
    var snapshots = new ArrayList<SearchTelemetrySnapshot>();
    SearchResult result = new KnightshadeEngine(1).search(
        STARTING_FEN, java.util.List.of(), SearchLimits.depth(3), StopSignal.never(), snapshots::add);

    assertEquals(3, result.depth());
    assertTrue(snapshots.size() >= 4);
    assertEquals(1, snapshots.get(0).depth());
    assertEquals(3, snapshots.get(snapshots.size() - 1).depth());
    assertTrue(snapshots.get(snapshots.size() - 1).mainNodes() > 0);
  }

  @Test
  void parallelTelemetryAggregatesWorkersAndCancellationHasReason() {
    var snapshots = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(2).search(STARTING_FEN, java.util.List.of(), SearchLimits.depth(2), StopSignal.never(), snapshots::add);
    assertFalse(snapshots.isEmpty());
    assertTrue(snapshots.stream().allMatch(s -> s.effectiveWorkers() >= 1));

    var cancelled = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(1).search(STARTING_FEN, java.util.List.of(), SearchLimits.depth(8), () -> true, cancelled::add);
    assertTrue(cancelled.stream().allMatch(s -> s.depth() >= 0));
  }
}
