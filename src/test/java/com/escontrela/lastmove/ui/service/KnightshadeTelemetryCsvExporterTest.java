package com.escontrela.lastmove.ui.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnightshadeTelemetryCsvExporterTest {
  @Test
  void exportsPonderDecisionAndSessionAggregatesWithoutPositionData() {
    var service = new KnightshadeTelemetryService();
    service.setEnabled(true);
    var context = new SearchTelemetryContext("game-7", "search-1",
        "secret-fen-position", PieceColor.WHITE, 12, Instant.parse("2026-09-24T00:00:00Z"),
        "depth=8", "Knightshade", "v3.5", List.of("secret-fen-position"));
    var hit = SearchTelemetrySnapshot.ponderDecision(context, SearchTelemetryEvent.PONDER_HIT, 7);
    var missContext = new SearchTelemetryContext("game-7", "search-2",
        "secret-fen-position", PieceColor.WHITE, 12, Instant.parse("2026-09-24T00:00:01Z"),
        "depth=8", "Knightshade", "v3.5", List.of("secret-fen-position"));
    var miss = SearchTelemetrySnapshot.ponderDecision(missContext, SearchTelemetryEvent.PONDER_MISS, 0);
    service.publish(SearchTelemetrySnapshot.ponderStarted(context));
    service.publish(hit);
    service.publish(miss);

    String csv = KnightshadeTelemetryCsvExporter.toCsv(service.samples(),
        Set.of("ponder hit rate", "ponder reused depth"), service);

    assertTrue(csv.startsWith("gameId,searchId,event,"));
    assertTrue(csv.contains("ponderReusedDepth,ponderHits,ponderMisses,ponderDecisions,ponderHitRate"));
    assertTrue(csv.contains("PONDER_HIT"));
    assertTrue(csv.contains("PONDER_STARTED"));
    assertTrue(csv.contains("ponderStarts"));
    assertTrue(csv.contains("PONDER_MISS"));
    assertTrue(csv.contains(",7,1,1,2,0.500000,7.00,7"));
    assertFalse(csv.contains("secret-fen-position"));
  }
}
