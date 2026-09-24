package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Immutable completed speculative continuation, tied to the exact request that produced it. */
public record PonderResult(
    PonderRequest request,
    MoveCommand expectedOpponentMove,
    PositionSnapshot expectedPosition,
    Optional<EngineAnalysisResult> continuation,
    boolean completed) {

  public PonderResult {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(expectedOpponentMove, "expectedOpponentMove must not be null");
    Objects.requireNonNull(expectedPosition, "expectedPosition must not be null");
    continuation = Objects.requireNonNull(continuation, "continuation must not be null");
    if (completed && continuation.isEmpty()) {
      throw new IllegalArgumentException("a completed ponder result must contain a continuation result");
    }
  }
}
