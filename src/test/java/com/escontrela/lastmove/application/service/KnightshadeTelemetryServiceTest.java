package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.StopReason;
import org.junit.jupiter.api.Test;

class KnightshadeTelemetryServiceTest {
  @Test
  void disabledServiceDoesNotRetainSnapshotsAndSessionResetIsIsolated() {
    var service = new KnightshadeTelemetryService();
    var snapshot = new SearchTelemetrySnapshot(1, null, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, StopReason.COMPLETED, 1);
    service.publish(snapshot);
    assertTrue(service.samples().isEmpty());
    service.setEnabled(true);
    service.publish(snapshot);
    assertEquals(1, service.samples().size());
    service.beginSession();
    assertTrue(service.samples().isEmpty());
  }
}
