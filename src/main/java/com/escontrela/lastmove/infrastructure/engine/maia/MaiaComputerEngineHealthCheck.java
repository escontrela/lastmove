package com.escontrela.lastmove.infrastructure.engine.maia;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Checks one Maia profile by requesting and validating a move from the initial position.
 *
 * <p>The probe mirrors the Sunfish health check: it resolves the executable and weights, starts the
 * process, asks for a move, and rejects the profile when {@code lc0} cannot produce a legal move.
 */
public final class MaiaComputerEngineHealthCheck implements ComputerEngineHealthCheck {

  private static final Duration PROBE_THINKING_TIME = Duration.ofMillis(250);

  private final MaiaComputerMoveEngineProvider provider;
  private final ChessRulesEngine rulesEngine;

  public MaiaComputerEngineHealthCheck(
      MaiaComputerMoveEngineProvider provider, ChessRulesEngine rulesEngine) {
    this.provider = Objects.requireNonNull(provider, "provider must not be null");
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return provider.descriptor();
  }

  @Override
  public CompletionStage<ComputerEngineHealth> check() {
    final MaiaRuntimeDetails runtime;
    final ComputerMoveEngine engine;
    try {
      runtime = provider.runtime();
      engine = provider.create(runtime);
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(unavailable(exception));
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
      MaiaRuntimeDetails runtime, PositionSnapshot position, MoveCommand move) {
    var result = rulesEngine.execute(position, move);
    if (!result.accepted()) {
      return ComputerEngineHealth.unavailable(
          descriptor(), "Maia returned an illegal probe move: " + move);
    }
    return ComputerEngineHealth.available(
        descriptor(),
        "Maia " + descriptor().version() + " is ready via " + runtime.executable()
            + "; legal probe move: " + move,
        move);
  }

  private ComputerEngineHealth unavailable(Throwable failure) {
    Throwable cause = rootCause(failure);
    String detail = cause.getMessage();
    if (detail == null || detail.isBlank()) {
      detail = cause.getClass().getSimpleName();
    }
    return ComputerEngineHealth.unavailable(descriptor(), "Maia is unavailable: " + detail);
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }
}
