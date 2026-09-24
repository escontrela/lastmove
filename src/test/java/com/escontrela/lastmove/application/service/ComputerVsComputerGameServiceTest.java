package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.escontrela.lastmove.application.computer.*;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.*;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ComputerVsComputerGameServiceTest {
  @Test
  void pondersDuringOpponentSearchAndPreservesIdentityForItsNextTurn() throws Exception {
    FakeEngine white = new FakeEngine("knightshade");
    FakeEngine black = new FakeEngine("opponent");
    var service = service(white, black, true, 8);
    try {
      var game = service.createGame(configuration()).toCompletableFuture().get(2, TimeUnit.SECONDS);
      white.nextSearch().result.complete(move("e2", "e4"));
      PonderRequest ponder = white.ponders.poll(2, TimeUnit.SECONDS);
      assertNotNull(ponder);
      Pending rival = black.nextSearch();
      assertTrue(ponder.shareWithSameGameRealSearch());
      assertEquals(game.gameId(), ponder.gameId());
      assertEquals(0, white.preemptions);
      assertNull(PonderResourceCoordinator.tryBeginAnalysis(), "strength analysis must yield");
      assertEquals(2, ponder.positionHistory().size());

      rival.result.complete(move("e7", "e5"));
      Pending nextTurn = white.nextSearch();
      assertEquals(ponder.gameId(), nextTurn.request.gameId());
      assertEquals(ponder.generation(), nextTurn.request.generation());
      assertEquals(3, nextTurn.request.positionHistory().size());
      service.stop(game.gameId());
      assertTrue(white.ponderCancelled);
    } finally {
      service.closeAll();
    }
  }

  @Test
  void skipsSpeculationWhenOffOrWhenOpponentUsesTheAvailableProcessors() throws Exception {
    for (boolean enabled : List.of(false, true)) {
      FakeEngine white = new FakeEngine("knightshade");
      FakeEngine black = new FakeEngine("opponent");
      var service = service(white, black, enabled, enabled ? 4 : 8);
      try {
        service.createGame(configuration()).toCompletableFuture().get(2, TimeUnit.SECONDS);
        white.nextSearch().result.complete(move("e2", "e4"));
        black.nextSearch();
        assertTrue(white.ponders.isEmpty());
      } finally {
        service.closeAll();
      }
    }
  }

  private static ComputerVsComputerGameService service(FakeEngine white, FakeEngine black,
      boolean enabled, int processors) {
    var repository = new ComputerEngineSettingsRepository() {
      public Optional<ComputerEngineSettings> findByEngineId(String id) { return Optional.empty(); }
      public void save(ComputerEngineSettings settings) {}
      public void deleteByEngineId(String id) {}
      public PonderSettings findPonderSettings() {
        return new PonderSettings(enabled, true, 2, Duration.ofMillis(100), 2, Duration.ofMillis(100));
      }
    };
    return new ComputerVsComputerGameService(new ChessGameFactory(new ChesspressoRulesEngine()),
        List.of(provider(white), provider(black)), Clock.systemUTC(), null,
        new ComputerEngineSettingsService(repository, "/tmp/sunfish", "/tmp/maia"), processors);
  }

  private static ComputerMoveEngineProvider provider(FakeEngine engine) {
    return new ComputerMoveEngineProvider() {
      public ComputerEngineDescriptor descriptor() { return engine.descriptor(); }
      public ComputerMoveEngine create() { return engine; }
      public int estimatedSearchWorkers() { return 4; }
    };
  }

  private static ComputerVsComputerConfiguration configuration() {
    return new ComputerVsComputerConfiguration("knightshade", "opponent", TimeControl.unlimited(),
        Duration.ofSeconds(1));
  }

  private static MoveCommand move(String from, String to) {
    return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
  }

  private record Pending(ComputerMoveRequest request, CompletableFuture<MoveCommand> result) {}

  private static final class FakeEngine implements ComputerMoveEngine {
    final String id;
    final BlockingQueue<Pending> searches = new LinkedBlockingQueue<>();
    final BlockingQueue<PonderRequest> ponders = new LinkedBlockingQueue<>();
    volatile Pending pending;
    volatile int preemptions;
    volatile boolean ponderCancelled;
    PonderResourceCoordinator.PonderLease lease;
    FakeEngine(String id) { this.id = id; }
    public ComputerEngineDescriptor descriptor() { return new ComputerEngineDescriptor(id, id, "test"); }
    public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    public boolean isRunning() { return true; }
    public boolean isThinking() { return pending != null && !pending.result.isDone(); }
    public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
      pending = new Pending(request, new CompletableFuture<>());
      searches.add(pending);
      return pending.result;
    }
    public void startPonder(PonderRequest request) {
      lease = PonderResourceCoordinator.tryStartPonder(request.gameId(), request.generation(),
          request.shareWithSameGameRealSearch(), () -> { preemptions++; cancelPonder(); });
      assertNotNull(lease);
      ponders.add(request);
    }
    public void cancelPonder() { ponderCancelled = true; if (lease != null) lease.close(); }
    public void cancelSearch() {
      if (pending != null) pending.result.completeExceptionally(new CancellationException());
    }
    public void close() { cancelPonder(); cancelSearch(); }
    Pending nextSearch() throws InterruptedException {
      Pending result = searches.poll(2, TimeUnit.SECONDS);
      assertNotNull(result, "next engine turn must start");
      return result;
    }
  }
}
