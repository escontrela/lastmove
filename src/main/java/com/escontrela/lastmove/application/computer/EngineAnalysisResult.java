package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.Objects;
import java.util.Optional;

/**
 * Engine-neutral outcome of analysing one position.
 *
 * <p>The chosen move is empty only when the position has no legal move (checkmate or stalemate).
 * The score and depth are engine-dependent and remain empty when an engine reports no such
 * information, such as a minimal UCI wrapper that only emits {@code bestmove}.
 */
public record EngineAnalysisResult(
    Optional<MoveCommand> bestMove, Optional<EngineScore> score, Optional<Integer> depth) {

  public EngineAnalysisResult {
    bestMove = Objects.requireNonNull(bestMove, "bestMove must not be null");
    score = Objects.requireNonNull(score, "score must not be null");
    depth = Objects.requireNonNull(depth, "depth must not be null");
  }

  public static EngineAnalysisResult of(
      MoveCommand bestMove, EngineScore score, Integer depth) {
    return new EngineAnalysisResult(
        Optional.of(Objects.requireNonNull(bestMove, "bestMove must not be null")),
        Optional.of(Objects.requireNonNull(score, "score must not be null")),
        Optional.ofNullable(depth));
  }

  /** Creates a result carrying only a chosen move, for engines that expose no evaluation. */
  public static EngineAnalysisResult moveOnly(MoveCommand bestMove) {
    return new EngineAnalysisResult(
        Optional.of(Objects.requireNonNull(bestMove, "bestMove must not be null")),
        Optional.empty(),
        Optional.empty());
  }

  /** Creates an empty result for a position with no legal move and no score information. */
  public static EngineAnalysisResult empty() {
    return new EngineAnalysisResult(Optional.empty(), Optional.empty(), Optional.empty());
  }
}
