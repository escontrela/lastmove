package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineHealth;
import com.escontrela.lastmove.application.computer.ComputerEngineHealthCheck;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class ComputerEngineHealthServiceTest {

  @Test
  void resolvesSunfishByItsStableEngineId() {
    ComputerEngineDescriptor descriptor =
        new ComputerEngineDescriptor("sunfish", "Sunfish", "test");
    MoveCommand move =
        new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty());
    ComputerEngineHealthCheck check =
        new ComputerEngineHealthCheck() {
          @Override
          public ComputerEngineDescriptor descriptor() {
            return descriptor;
          }

          @Override
          public java.util.concurrent.CompletionStage<ComputerEngineHealth> check() {
            return CompletableFuture.completedFuture(
                ComputerEngineHealth.available(descriptor, "ready", move));
          }
        };

    var health =
        new ComputerEngineHealthService(List.of(check))
            .checkSunfish()
            .toCompletableFuture()
            .join();

    assertEquals(move, health.probeMove().orElseThrow());
  }

  @Test
  void rejectsAnUnknownEngineId() {
    var service = new ComputerEngineHealthService(List.of());

    assertThrows(NoSuchElementException.class, () -> service.check("unknown"));
  }
}
