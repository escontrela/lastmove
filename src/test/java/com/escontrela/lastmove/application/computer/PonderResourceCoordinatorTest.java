package com.escontrela.lastmove.application.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PonderResourceCoordinatorTest {
  @Test
  void realSearchCancelsPonderAndBlocksNewAdmissionUntilItCompletes() {
    AtomicInteger cancellations = new AtomicInteger();
    PonderResourceCoordinator.PonderLease ponder =
        PonderResourceCoordinator.tryStartPonder(cancellations::incrementAndGet);
    var realSearch = PonderResourceCoordinator.beginRealSearch();

    assertEquals(1, cancellations.get());
    assertNull(PonderResourceCoordinator.tryStartPonder(() -> {}));
    realSearch.close();
    ponder.close();

    var admitted = PonderResourceCoordinator.tryStartPonder(() -> {});
    assertNotNull(admitted);
    admitted.close();
  }
}
