package com.escontrela.lastmove.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.training.memory.MemoryGameBoardPositionService;
import com.escontrela.lastmove.application.training.memory.MemoryGameCancellable;
import com.escontrela.lastmove.application.training.memory.MemoryGameClock;
import com.escontrela.lastmove.application.training.memory.MemoryGameOrchestrator;
import com.escontrela.lastmove.application.training.memory.MemoryGamePosition;
import com.escontrela.lastmove.application.training.memory.MemoryGamePositionSelector;
import com.escontrela.lastmove.application.training.memory.MemoryGameUiDispatcher;
import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemoryGameViewModelTest {
  private static final String POSITION_A =
      "rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
  private static final String POSITION_B =
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

  @Test
  void offersRestartOnlyAfterTheFinishedFirstAttempt() {
    FakeClock clock = new FakeClock();
    MemoryGameViewModel viewModel = viewModel(clock);
    viewModel.start();
    assertFalse(viewModel.finished());
    assertFalse(viewModel.canRestart());

    clock.advanceTo(Duration.ofSeconds(180));
    assertTrue(viewModel.finished());
    assertTrue(viewModel.canRestart());

    viewModel.restart();
    assertFalse(viewModel.finished());
    assertFalse(viewModel.canRestart());

    clock.advanceTo(Duration.ofSeconds(180));
    assertTrue(viewModel.finished());
    assertFalse(viewModel.canRestart());
  }

  @Test
  void exposesTheFinalResultWithScoreAndVerdict() {
    FakeClock clock = new FakeClock();
    MemoryGameViewModel viewModel = viewModel(clock);
    viewModel.start();
    clock.advanceTo(Duration.ofSeconds(5));
    answerCorrectly(viewModel);

    clock.advanceTo(Duration.ofSeconds(180));

    var snapshot = viewModel.snapshot().orElseThrow();
    assertEquals(MemoryGameState.FINISHED, snapshot.state());
    assertEquals(1, snapshot.score());
    assertEquals(1, snapshot.maxPossibleScore());
    assertTrue(snapshot.successful());
    assertTrue(viewModel.canRestart());
  }

  private static void answerCorrectly(MemoryGameViewModel viewModel) {
    viewModel.snapshot().orElseThrow().challenge().orElseThrow().hiddenPieces()
        .forEach(piece -> {
          viewModel.placePiece(piece.square(), piece.type(), piece.color());
        });
  }

  private static MemoryGameViewModel viewModel(FakeClock clock) {
    ChesspressoRulesEngine rules = new ChesspressoRulesEngine();
    MemoryGamePositionSelector selector = new MemoryGamePositionSelector(
        () -> List.of(
            new MemoryGamePosition("game-0", POSITION_A),
            new MemoryGamePosition("game-1", POSITION_B)),
        rules,
        new Random(4));
    MemoryGameOrchestrator orchestrator =
        new MemoryGameOrchestrator(selector, clock, MemoryGameUiDispatcher.immediate());
    return new MemoryGameViewModel(orchestrator, new MemoryGameBoardPositionService(rules));
  }

  private static final class FakeClock implements MemoryGameClock {
    private final List<Scheduled> scheduled = new ArrayList<>();
    private Duration elapsed = Duration.ZERO;

    @Override public void reset() { elapsed = Duration.ZERO; scheduled.clear(); }
    @Override public Duration elapsed() { return elapsed; }
    @Override public MemoryGameCancellable schedule(Duration delay, Runnable callback) {
      Scheduled entry = new Scheduled(elapsed.plus(delay), callback);
      scheduled.add(entry);
      return () -> entry.cancelled = true;
    }
    @Override public void cancelAll() { scheduled.forEach(entry -> entry.cancelled = true); }
    void advanceTo(Duration target) {
      // Fire each due callback at its own due instant (like a real clock), so a long
      // jump still runs the feedback -> next round -> memorization chain in order.
      while (true) {
        Scheduled next = null;
        for (Scheduled entry : scheduled) {
          if (!entry.cancelled && entry.when.compareTo(target) <= 0
              && (next == null || entry.when.compareTo(next.when) < 0)) {
            next = entry;
          }
        }
        if (next == null) break;
        next.cancelled = true;
        elapsed = next.when;
        next.callback.run();
      }
      elapsed = target;
    }
    private static final class Scheduled {
      private final Duration when;
      private final Runnable callback;
      private boolean cancelled;
      private Scheduled(Duration when, Runnable callback) { this.when = when; this.callback = callback; }
    }
  }
}
