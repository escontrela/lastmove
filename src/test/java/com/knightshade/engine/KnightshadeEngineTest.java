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
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.knightshade.engine.api.StopReason;

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
    assertEquals(SearchTelemetryEvent.SEARCH_FINISHED, snapshots.getLast().event());
    assertEquals(StopReason.DEPTH_LIMIT, snapshots.getLast().stopReason());
    assertTrue(snapshots.stream().filter(s -> s.event() == SearchTelemetryEvent.SEARCH_FINISHED).count() == 1);
  }

  @Test
  void parallelTelemetryAggregatesWorkersAndCancellationHasReason() {
    var snapshots = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(2).search(STARTING_FEN, java.util.List.of(), SearchLimits.depth(2), StopSignal.never(), snapshots::add);
    assertFalse(snapshots.isEmpty());
    assertTrue(snapshots.stream().allMatch(s -> s.activeWorkers() >= 1));

    var cancelled = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(1).search(STARTING_FEN, java.util.List.of(), SearchLimits.depth(8), () -> true, cancelled::add);
    assertTrue(cancelled.stream().allMatch(s -> s.depth() >= 0));
    assertEquals(StopReason.CANCELLED, cancelled.getLast().stopReason());
  }

  @Test
  void classifiesMateAndTimeLimitAsDistinctTerminalEvents() {
    var mate = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(1).search(
        "6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1", java.util.List.of(),
        SearchLimits.depth(4), StopSignal.never(), mate::add);
    assertEquals(StopReason.MATE, mate.getLast().stopReason());

    var timed = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(1).search(
        STARTING_FEN, java.util.List.of(), SearchLimits.timeOnly(Duration.ofMillis(1)),
        StopSignal.never(), timed::add);
    assertEquals(StopReason.TIME_LIMIT, timed.getLast().stopReason());

    var checkmated = new ArrayList<SearchTelemetrySnapshot>();
    new KnightshadeEngine(2).search(
        "7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", java.util.List.of(),
        SearchLimits.depth(4), StopSignal.never(), checkmated::add);
    assertEquals(StopReason.MATE, checkmated.getLast().stopReason());
  }

  @Test
  void assignsReproducibleMetadataToEachSearch() {
    var first = new ArrayList<SearchTelemetrySnapshot>();
    var second = new ArrayList<SearchTelemetrySnapshot>();
    KnightshadeEngine engine = new KnightshadeEngine(1);

    engine.search(STARTING_FEN, java.util.List.of(STARTING_FEN), SearchLimits.depth(1),
        StopSignal.never(), first::add);
    engine.search(STARTING_FEN, java.util.List.of(STARTING_FEN), SearchLimits.depth(1),
        StopSignal.never(), second::add);

    var firstContext = first.getLast().context();
    var secondContext = second.getLast().context();
    assertEquals(STARTING_FEN, firstContext.rootFen());
    assertEquals(java.util.List.of(STARTING_FEN), firstContext.positionHistory());
    assertEquals(com.escontrela.lastmove.domain.common.PieceColor.WHITE, firstContext.sideToMove());
    assertEquals(1, firstContext.fullmoveNumber());
    assertFalse(firstContext.searchId().equals(secondContext.searchId()));
    assertTrue(firstContext.startedAt().isBefore(first.getLast().observedAt())
        || firstContext.startedAt().equals(first.getLast().observedAt()));
  }

  @Test
  void telemetryDoesNotChangeSequentialSearchResult() {
    KnightshadeEngine engine = new KnightshadeEngine(1);
    SearchResult withoutTelemetry =
        engine.search(STARTING_FEN, SearchLimits.depth(4), StopSignal.never());
    var snapshots = new ArrayList<SearchTelemetrySnapshot>();
    SearchResult withTelemetry =
        engine.search(STARTING_FEN, java.util.List.of(), SearchLimits.depth(4), StopSignal.never(), snapshots::add);

    assertEquals(withoutTelemetry.move(), withTelemetry.move());
    assertEquals(withoutTelemetry.score(), withTelemetry.score());
    assertEquals(withoutTelemetry.depth(), withTelemetry.depth());
    assertFalse(snapshots.isEmpty());
  }
}
