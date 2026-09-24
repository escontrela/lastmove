package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnightshadeTelemetryServiceTest {
  @Test
  void disabledServiceDoesNotRetainSnapshotsAndSessionResetIsIsolated() {
    var service = new KnightshadeTelemetryService();
    var context = new SearchTelemetryContext("game-1", "search-1",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        PieceColor.WHITE, 1, Instant.now(), "depth=1", "test", "test", List.of());
    var snapshot = new SearchTelemetrySnapshot(context, SearchTelemetryEvent.SEARCH_FINISHED,
        Instant.now(), 1, null, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
        0, 0, 0, 0, 0, 0, 0, StopReason.DEPTH_LIMIT, 1);
    service.publish(snapshot);
    assertTrue(service.samples().isEmpty());
    assertEquals(0, service.ponderDecisions());
    service.setEnabled(true);
    service.publish(snapshot);
    assertEquals(1, service.samples().size());
    service.beginSession();
    assertTrue(service.samples().isEmpty());
  }

  @Test
  void sessionIdsAreIsolatedAndOnlyTerminalEventsCountAsStops() {
    var service = new KnightshadeTelemetryService();
    service.setEnabled(true);
    service.beginSession("game-one");
    assertEquals("game-one", service.gameId());
    service.beginSession("game-two");
    assertEquals("game-two", service.gameId());
    assertTrue(service.stopReasonCounts().isEmpty());
  }

  @Test
  void ponderDecisionsCountOnlyValidatedHitsAndMissesAndReuseDepthOncePerDecision() {
    var service = new KnightshadeTelemetryService();
    service.setEnabled(true);
    var context = new SearchTelemetryContext("game", "search", "fen", PieceColor.WHITE, 1,
        Instant.now(), "limits", "test", "test", List.of());

    service.publish(SearchTelemetrySnapshot.ponderStarted(context));
    service.publish(SearchTelemetrySnapshot.ponderStarted(context));
    assertEquals(1, service.ponderStarts());
    assertEquals(0, service.ponderDecisions());

    service.publish(SearchTelemetrySnapshot.ponderDecision(context,
        SearchTelemetryEvent.PONDER_HIT, 6));
    var duplicateHit = SearchTelemetrySnapshot.ponderDecision(context,
        SearchTelemetryEvent.PONDER_HIT, 6);
    service.publish(duplicateHit);
    service.publish(duplicateHit);
    var secondDecisionContext = new SearchTelemetryContext("game", "search-2", "fen",
        PieceColor.WHITE, 2, Instant.now(), "limits", "test", "test", List.of());
    service.publish(SearchTelemetrySnapshot.ponderDecision(secondDecisionContext,
        SearchTelemetryEvent.PONDER_HIT, 8));
    var missContext = new SearchTelemetryContext("game", "search-3", "fen", PieceColor.WHITE,
        3, Instant.now(), "limits", "test", "test", List.of());
    service.publish(SearchTelemetrySnapshot.ponderDecision(missContext,
        SearchTelemetryEvent.PONDER_MISS, 0));

    assertEquals(2, service.ponderHits());
    assertEquals(1, service.ponderMisses());
    assertEquals(3, service.ponderDecisions());
    assertEquals(2.0 / 3.0, service.ponderHitRate().orElseThrow());
    assertEquals(7.0, service.ponderReusedDepthAverage().orElseThrow());
    assertEquals(8, service.ponderReusedDepthMax().orElseThrow());
    assertEquals(8, service.lastPonderReusedDepth().orElseThrow());

    service.beginSession("next-game");
    assertEquals(0, service.ponderStarts());
    assertEquals(0, service.ponderDecisions());
    assertTrue(service.ponderHitRate().isEmpty());
    assertTrue(service.ponderReusedDepthAverage().isEmpty());
    assertTrue(service.ponderReusedDepthMax().isEmpty());
  }

  @Test
  void ponderCardsCanBeFilteredWithoutChangingTheirCounters() {
    var service = new KnightshadeTelemetryService();
    service.setVisibleMetrics(Set.of("ponder hit rate"));

    assertEquals(Set.of("ponder hit rate"), service.visibleMetrics());
    assertFalse(service.visibleMetrics().contains("ponder reused depth"));
    assertEquals(0, service.ponderDecisions());
  }
}
