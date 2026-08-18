package com.escontrela.lastmove.infrastructure.engine.knightshade;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.service.FenService;
import com.knightshade.engine.KnightshadeEngine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process {@link ComputerMoveEngine} backed by the embedded Knightshade engine.
 *
 * <p>The adapter keeps the same FEN-in / UCI-move-out shape as the external Sunfish UCI engine, but
 * runs the search in memory on a virtual thread. The position is serialized with {@link FenService}
 * and the resulting engine move is mapped directly onto a domain {@link MoveCommand}.
 */
public final class KnightshadeMoveEngine implements ComputerMoveEngine {

  private final KnightshadeEngine engine;
  private final FenService fenService;
  private final ComputerEngineDescriptor descriptor;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("knightshade-", 0).factory());
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean thinking = new AtomicBoolean();
  private final AtomicBoolean cancellationRequested = new AtomicBoolean();

  public KnightshadeMoveEngine(
      KnightshadeEngine engine, FenService fenService, ComputerEngineDescriptor descriptor) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public CompletionStage<Void> start() {
    if (closed.get()) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public boolean isRunning() {
    return !closed.get();
  }

  @Override
  public boolean isThinking() {
    return thinking.get();
  }

  @Override
  public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
    ComputerMoveRequest required =
        Objects.requireNonNull(request, "request must not be null");
    if (closed.get()) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    thinking.set(true);
    cancellationRequested.set(false);
    try {
      return CompletableFuture.supplyAsync(() -> chooseMoveBlocking(required), executor);
    } catch (RejectedExecutionException exception) {
      thinking.set(false);
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public void cancelSearch() {
    cancellationRequested.set(true);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      cancellationRequested.set(true);
      executor.shutdownNow();
    }
  }

  private MoveCommand chooseMoveBlocking(ComputerMoveRequest request) {
    try {
      String fen = fenService.fromSnapshot(request.position()).getValue();
      SearchResult result =
          engine.search(
              fen,
              SearchLimits.timeOnly(request.maximumThinkingTime()),
              cancellationRequested::get);
      if (result.move() == null) {
        throw new ComputerEngineException("Knightshade found no playable move");
      }
      return new MoveCommand(
          result.move().from(),
          result.move().to(),
          Optional.ofNullable(result.move().promotion()));
    } finally {
      thinking.set(false);
      cancellationRequested.set(false);
    }
  }

  private ComputerEngineException closedEngineException() {
    return new ComputerEngineException("The Knightshade engine has already been closed");
  }
}
