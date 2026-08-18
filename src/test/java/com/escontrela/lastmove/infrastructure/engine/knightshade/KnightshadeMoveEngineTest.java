package com.escontrela.lastmove.infrastructure.engine.knightshade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.knightshade.engine.KnightshadeEngine;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v0"), provider.descriptor());
    assertTrue(provider.create() instanceof KnightshadeMoveEngine);
  }

  private KnightshadeMoveEngine engine() {
    ComputerEngineDescriptor descriptor =
        new ComputerEngineDescriptor("knightshade", "Knightshade", "v0");
    return new KnightshadeMoveEngine(new KnightshadeEngine(), fenService, descriptor);
  }
}
