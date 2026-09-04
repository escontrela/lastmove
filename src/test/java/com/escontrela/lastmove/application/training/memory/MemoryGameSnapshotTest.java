package com.escontrela.lastmove.application.training.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryGameSnapshotTest {

  @Test
  void passesAtTheExactSixtyPercentThreshold() {
    MemoryGameSnapshot snapshot = snapshot(MemoryGameState.FINISHED, 1, 3, 5);

    assertEquals(0.60d, snapshot.successRate(), 1e-9);
    assertTrue(snapshot.successful());
  }

  @Test
  void failsJustBelowTheThreshold() {
    MemoryGameSnapshot snapshot = snapshot(MemoryGameState.FINISHED, 1, 5, 9);

    assertFalse(snapshot.successful());
  }

  @Test
  void failsWhenNoRoundWasEvaluated() {
    MemoryGameSnapshot snapshot = snapshot(MemoryGameState.FINISHED, 1, 0, 0);

    assertEquals(0.0d, snapshot.successRate());
    assertFalse(snapshot.successful());
  }

  @Test
  void offersARestartOnlyForTheFinishedFirstAttempt() {
    assertTrue(snapshot(MemoryGameState.FINISHED, 1, 0, 0).canRestart());
    assertFalse(snapshot(MemoryGameState.FINISHED, 2, 0, 0).canRestart());
    assertFalse(snapshot(MemoryGameState.MEMORIZING, 1, 0, 0).canRestart());
    assertFalse(snapshot(MemoryGameState.GUESSING, 1, 0, 0).canRestart());
  }

  private static MemoryGameSnapshot snapshot(MemoryGameState state, int attempt, int score, int max) {
    return new MemoryGameSnapshot(
        state,
        attempt,
        score,
        max,
        Duration.ZERO,
        Duration.ZERO,
        Optional.empty(),
        Optional.empty(),
        false,
        false,
        List.of());
  }
}
