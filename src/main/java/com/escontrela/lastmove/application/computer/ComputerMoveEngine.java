package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.concurrent.CompletionStage;

/**
 * Application boundary for an asynchronous computer chess opponent.
 *
 * <p>Implementations may launch a UCI process or use another technology, but must return an
 * engine-neutral {@link MoveCommand}. The progressive game remains responsible for validating and
 * applying that proposed move.
 */
public interface ComputerMoveEngine extends AutoCloseable {

  /** Returns the engine identity exposed by setup and game screens. */
  ComputerEngineDescriptor descriptor();

  /** Starts the engine and completes after it is ready to receive positions. Idempotent. */
  CompletionStage<Void> start();

  /** Returns whether the underlying engine process is alive and ready. */
  boolean isRunning();

  /** Returns whether a move search is currently in progress. */
  boolean isThinking();

  /** Chooses one move asynchronously without mutating the supplied position. */
  CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request);

  /**
   * Analyses one position asynchronously without mutating it.
   *
   * <p>The default implementation delegates to {@link #chooseMove(ComputerMoveRequest)} and
   * returns the chosen move without an evaluation. Engines that expose a score or search depth
   * override this method to include that information.
   */
  default CompletionStage<EngineAnalysisResult> analyze(ComputerMoveRequest request) {
    return chooseMove(request).thenApply(EngineAnalysisResult::moveOnly);
  }

  /** Requests cancellation of the active search. Does nothing when no search is running. */
  void cancelSearch();

  /** Stops all work and releases process, stream and executor resources. Idempotent. */
  @Override
  void close();
}
