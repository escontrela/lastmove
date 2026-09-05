package com.escontrela.lastmove.application.training.storm;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.escontrela.lastmove.application.training.TrainingCancellable;
import com.escontrela.lastmove.application.training.TrainingClock;
import com.escontrela.lastmove.application.training.TrainingUiDispatcher;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StormGameOrchestratorTest {
  @Test void publishesAnEmptyReadyStateWithoutStartingAClock() {
    StormGameOrchestrator orchestrator = new StormGameOrchestrator(
        new StormGameExerciseSelector(List::of), List::of,
        new ChessGameFactory(new ChesspressoRulesEngine()), new NoopClock(), TrainingUiDispatcher.immediate());
    List<StormGameSnapshot> snapshots = new ArrayList<>(); orchestrator.observe(snapshots::add); orchestrator.start();
    assertTrue(snapshots.getLast().emptySource());
    assertTrue(snapshots.getLast().remainingTime().equals(Duration.ofMinutes(3)));
  }
  private static final class NoopClock implements TrainingClock { public void reset() {} public Duration elapsed() { return Duration.ZERO; } public TrainingCancellable schedule(Duration d, Runnable r) { return () -> {}; } public void cancelAll() {} }
}
