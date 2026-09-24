package com.escontrela.lastmove.infrastructure.engine.knightshade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.PonderRequest;
import com.escontrela.lastmove.application.computer.PonderSettings;
import com.escontrela.lastmove.application.computer.PonderState;
import com.escontrela.lastmove.application.computer.PonderResourceCoordinator;
import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.knightshade.engine.KnightshadeEngine;
import java.time.Duration;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

class KnightshadeMoveEngineTest {

  private final ChesspressoRulesEngine rulesEngine = new ChesspressoRulesEngine();
  private final FenService fenService = new FenService();

  @Test
  void exposesTheKnightshadeDescriptor() {
    KnightshadeMoveEngine engine = engine();

    assertEquals("knightshade", engine.descriptor().id());
    assertEquals("Knightshade", engine.descriptor().displayName());
  }

  @Test
  void returnsALegalMoveFromTheStartingPosition() {
    PositionSnapshot starting = rulesEngine.startingPosition();
    KnightshadeMoveEngine engine = engine();

    MoveCommand move =
        engine
            .chooseMove(new ComputerMoveRequest(starting, Duration.ofMillis(500)))
            .toCompletableFuture()
            .join();

    assertTrue(rulesEngine.execute(starting, move).accepted(), "engine move must be legal: " + move);
    engine.close();
  }

  @Test
  void strengthAnalysisDoesNotPublishMatchOrPonderTelemetry() {
    var telemetry = new KnightshadeTelemetryService();
    telemetry.setEnabled(true);
    KnightshadeMoveEngine engine = new KnightshadeMoveEngine(new KnightshadeEngine(), fenService,
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5"), telemetry);
    try {
      engine.analyze(new ComputerMoveRequest(rulesEngine.startingPosition(), Duration.ofMillis(300)))
          .toCompletableFuture().join();

      assertTrue(telemetry.samples().isEmpty());
      assertEquals(0, telemetry.ponderDecisions());
    } finally {
      engine.close();
    }
  }

  @Test
  void reportsRunningUntilClosedAndThenRejectsMoves() {
    KnightshadeMoveEngine engine = engine();

    engine.start().toCompletableFuture().join();
    assertTrue(engine.isRunning());

    engine.close();
    assertFalse(engine.isRunning());
    assertTrue(
        engine
            .chooseMove(new ComputerMoveRequest(rulesEngine.startingPosition(), Duration.ofMillis(100)))
            .toCompletableFuture()
            .isCompletedExceptionally());
  }

  @Test
  void providerCreatesFreshEnginesWithTheKnightshadeId() {
    KnightshadeMoveEngineProvider provider = new KnightshadeMoveEngineProvider(fenService);

    assertEquals(
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5"), provider.descriptor());
    assertTrue(provider.create() instanceof KnightshadeMoveEngine);
  }

  @Test
  void ponderingIsOptInAndCancellationIsScopedToThisEngineInstance() {
    PositionSnapshot position = rulesEngine.startingPosition();
    PonderSettings enabled = new PonderSettings(true, true, 4, Duration.ofMillis(100),
        8, Duration.ofMillis(1000));
    PonderRequest request = new PonderRequest(GameId.random(), 1, position, List.of(position),
        enabled, "knightshade-default-v1");
    KnightshadeMoveEngine first = new KnightshadeMoveEngine(new KnightshadeEngine(), fenService,
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5"), null, enabled);
    KnightshadeMoveEngine second = engine();

    first.startPonder(request);
    second.startPonder(request);
    assertEquals(PonderState.PREDICTING, first.ponderState());
    assertEquals(PonderState.IDLE, second.ponderState());

    first.cancelPonder();
    assertEquals(PonderState.IDLE, first.ponderState());
    first.close();
    second.close();
  }

  @Test
  void matchingOpponentReplyResumesThePreparedContinuation() throws Exception {
    PositionSnapshot starting = rulesEngine.startingPosition();
    PositionSnapshot afterOurMove = rulesEngine.execute(starting, move("e2e4")).newSnapshot();
    PonderSettings settings = new PonderSettings(true, true, 2, Duration.ofSeconds(2),
        2, Duration.ofSeconds(2));
    GameId gameId = GameId.random();
    KnightshadeTelemetryService telemetry = new KnightshadeTelemetryService();
    telemetry.setEnabled(true);
    telemetry.beginSession(gameId.value().toString());
    KnightshadeMoveEngine engine = new KnightshadeMoveEngine(new KnightshadeEngine(), fenService,
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5"), telemetry, settings);
    engine.startPonder(new PonderRequest(gameId, 1, afterOurMove,
        List.of(starting, afterOurMove), settings, "knightshade-default-v1"));
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
      while (engine.ponderState() != PonderState.READY && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertEquals(PonderState.READY, engine.ponderState());
      var prepared = engine.preparedPonderContext();
      PositionSnapshot afterPredictedReply = rulesEngine.positionFrom(Fen.of(prepared.expectedFen()));
      List<PositionSnapshot> actualHistory = new ArrayList<>(List.of(starting, afterOurMove));
      actualHistory.add(afterPredictedReply);

      MoveCommand continuation = engine.chooseMove(new ComputerMoveRequest(afterPredictedReply,
              Duration.ofSeconds(1), actualHistory, gameId, 1))
          .toCompletableFuture().get();

      assertTrue(rulesEngine.execute(afterPredictedReply, continuation).accepted());
      assertEquals(PonderState.HIT, engine.lastPonderOutcome());
      assertEquals(1, telemetry.ponderHits());
      assertEquals(1, telemetry.ponderStarts());
      assertEquals(0, telemetry.ponderMisses());
      assertTrue(telemetry.ponderReusedDepthAverage().isPresent());
    } finally {
      engine.close();
    }
  }

  @Test
  void differentLegalOpponentReplyCountsMissAndUsesNormalLegalFallback() throws Exception {
    PositionSnapshot starting = rulesEngine.startingPosition();
    PositionSnapshot afterOurMove = rulesEngine.execute(starting, move("e2e4")).newSnapshot();
    PonderSettings settings = new PonderSettings(true, true, 2, Duration.ofSeconds(2),
        2, Duration.ofSeconds(2));
    GameId gameId = GameId.random();
    KnightshadeTelemetryService telemetry = new KnightshadeTelemetryService();
    telemetry.setEnabled(true);
    telemetry.beginSession(gameId.value().toString());
    KnightshadeMoveEngine engine = new KnightshadeMoveEngine(new KnightshadeEngine(), fenService,
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5"), telemetry, settings);
    engine.startPonder(new PonderRequest(gameId, 1, afterOurMove,
        List.of(starting, afterOurMove), settings, "knightshade-default-v1"));
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
      while (engine.ponderState() != PonderState.READY && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      assertEquals(PonderState.READY, engine.ponderState());
      String predictedFen = engine.preparedPonderContext().expectedFen();
      var e5 = rulesEngine.execute(afterOurMove, move("e7e5"));
      var c5 = rulesEngine.execute(afterOurMove, move("c7c5"));
      PositionSnapshot actualReply = e5.accepted() && !fenService.fromSnapshot(e5.newSnapshot()).getValue().equals(predictedFen)
          ? e5.newSnapshot() : c5.newSnapshot();
      assertTrue(rulesEngine.execute(afterOurMove, move("e7e5")).accepted());
      assertFalse(fenService.fromSnapshot(actualReply).getValue().equals(predictedFen));
      List<PositionSnapshot> history = List.of(starting, afterOurMove, actualReply);

      MoveCommand continuation = engine.chooseMove(new ComputerMoveRequest(actualReply,
              Duration.ofSeconds(1), history, gameId, 1))
          .toCompletableFuture().get();

      assertTrue(rulesEngine.execute(actualReply, continuation).accepted());
      assertEquals(0, telemetry.ponderHits());
      assertEquals(1, telemetry.ponderMisses());
    } finally {
      engine.close();
    }
  }

  @Test
  void sameGameOpponentSearchDoesNotCancelAdmittedPonder() throws Exception {
    var initial = rulesEngine.startingPosition();
    var afterOurMove = rulesEngine.execute(initial, move("e2e4")).newSnapshot();
    var settings = new PonderSettings(true, true, 1, Duration.ofSeconds(2),
        1, Duration.ofSeconds(2));
    var gameId = GameId.random();
    var telemetry = new KnightshadeTelemetryService();
    telemetry.setEnabled(true);
    var started = new java.util.concurrent.CountDownLatch(1);
    var proceed = new java.util.concurrent.CountDownLatch(1);
    telemetry.subscribe(sample -> {
      if (sample.event() == com.knightshade.engine.api.SearchTelemetryEvent.PONDER_STARTED) {
        started.countDown();
        try { proceed.await(5, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
      }
    });
    var descriptor = new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5");
    var pondering = new KnightshadeMoveEngine(new KnightshadeEngine(1), fenService,
        descriptor, telemetry, settings);
    var opponent = new KnightshadeMoveEngine(new KnightshadeEngine(1), fenService, descriptor);
    try {
      pondering.startPonder(new PonderRequest(gameId, 1, afterOurMove,
          List.of(initial, afterOurMove), settings, "knightshade-default-v1", true));
      assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
      var reply = opponent.chooseMove(new ComputerMoveRequest(afterOurMove, Duration.ofMillis(20),
          List.of(initial, afterOurMove), gameId, 0)).toCompletableFuture()
          .get(2, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(rulesEngine.execute(afterOurMove, reply).accepted());
      assertEquals(1, telemetry.ponderStarts());
      proceed.countDown();
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (pondering.ponderState() != PonderState.READY && System.nanoTime() < deadline) {
        Thread.sleep(5);
      }
      assertEquals(PonderState.READY, pondering.ponderState());
    } finally {
      proceed.countDown();
      pondering.close();
      opponent.close();
    }
  }

  private static MoveCommand move(String uci) {
    return new MoveCommand(com.escontrela.lastmove.domain.common.Square.of(uci.substring(0, 2)),
        com.escontrela.lastmove.domain.common.Square.of(uci.substring(2, 4)), Optional.empty());
  }

  private KnightshadeMoveEngine engine() {
    ComputerEngineDescriptor descriptor =
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v3.5");
    return new KnightshadeMoveEngine(new KnightshadeEngine(), fenService, descriptor);
  }

  @AfterEach
  void waitForSpeculativeWorkerToReleaseItsGlobalPermit() throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (System.nanoTime() < deadline) {
      var permit = PonderResourceCoordinator.tryStartPonder(() -> {});
      if (permit != null) {
        permit.close();
        return;
      }
      Thread.sleep(10);
    }
  }
}
