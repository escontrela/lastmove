package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import java.util.Objects;
import java.util.Optional;

/** Immutable, engine-neutral presentation state for a position evaluation. */
public record EngineEvaluationState(
    ComputerEngineDescriptor engine,
    Optional<String> score,
    Optional<Integer> depth,
    Optional<String> bestMove,
    Optional<Long> nodes,
    boolean searching) {

  public EngineEvaluationState {
    engine = Objects.requireNonNull(engine, "engine must not be null");
    score = Objects.requireNonNull(score, "score must not be null");
    depth = Objects.requireNonNull(depth, "depth must not be null");
    bestMove = Objects.requireNonNull(bestMove, "bestMove must not be null");
    nodes = Objects.requireNonNull(nodes, "nodes must not be null");
  }

  public static EngineEvaluationState idle(ComputerEngineDescriptor engine) {
    return new EngineEvaluationState(
        engine, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);
  }

  public static EngineEvaluationState searching(ComputerEngineDescriptor engine) {
    return new EngineEvaluationState(
        engine, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), true);
  }
}
