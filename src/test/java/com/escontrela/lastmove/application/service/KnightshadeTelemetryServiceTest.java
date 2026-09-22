package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.time.Instant;
import java.util.List;
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
}
