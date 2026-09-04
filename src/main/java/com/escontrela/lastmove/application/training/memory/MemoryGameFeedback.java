package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;

/** Result for one hidden square, retained briefly so the UI can explain a submission. */
public record MemoryGameFeedback(Square square, MemoryGamePiece expected, MemoryGamePiece submitted, boolean correct) {
  public MemoryGameFeedback {
    Objects.requireNonNull(square, "square must not be null");
    Objects.requireNonNull(expected, "expected must not be null");
  }
}
