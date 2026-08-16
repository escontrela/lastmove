package com.escontrela.lastmove.infrastructure.engine.sunfish;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineHealth;
import com.escontrela.lastmove.application.computer.ComputerEngineHealthCheck;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Component;

/** Checks the configured Sunfish process by requesting and validating a move from the initial FEN. */
@Component
public final class SunfishComputerEngineHealthCheck implements ComputerEngineHealthCheck {

  private static final Duration PROBE_THINKING_TIME = Duration.ofMillis(250);

  private final SunfishComputerMoveEngineProvider provider;
  private final ChessRulesEngine rulesEngine;

  public SunfishComputerEngineHealthCheck(
      SunfishComputerMoveEngineProvider provider, ChessRulesEngine rulesEngine) {
    this.provider = Objects.requireNonNull(provider, "provider must not be null");
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return provider.descriptor();
  }

  @Override
  public CompletionStage<ComputerEngineHealth> check() {
    final SunfishRuntimeDetails runtime;
    final ComputerMoveEngine engine;
    try {
      runtime = provider.runtime();
      engine = provider.create(runtime);
    } catch (RuntimeException exception) {
      return java.util.concurrent.CompletableFuture.completedFuture(unavailable(exception));
    }

    PositionSnapshot probePosition = rulesEngine.startingPosition();
    return engine
        .start()
        .thenCompose(
            ignored ->
                engine.chooseMove(new ComputerMoveRequest(probePosition, PROBE_THINKING_TIME)))
        .handle(
            (move, failure) -> {
              engine.close();
              if (failure != null) {
                return unavailable(failure);
              }
              return validateProbeMove(runtime, probePosition, move);
            });
  }

  private ComputerEngineHealth validateProbeMove(
      SunfishRuntimeDetails runtime, PositionSnapshot position, MoveCommand move) {
    var result = rulesEngine.execute(position, move);
    if (!result.accepted()) {
      return ComputerEngineHealth.unavailable(
          descriptor(), "Sunfish returned an illegal probe move: " + move);
    }
    return ComputerEngineHealth.available(
        descriptor(),
        "Sunfish is ready via " + runtime.interpreter() + "; legal probe move: " + move,
        move);
  }

  private ComputerEngineHealth unavailable(Throwable failure) {
    Throwable cause = rootCause(failure);
    String detail = cause.getMessage();
    if (detail == null || detail.isBlank()) {
      detail = cause.getClass().getSimpleName();
    }
    return ComputerEngineHealth.unavailable(descriptor(), "Sunfish is unavailable: " + detail);
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }
}
