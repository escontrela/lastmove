package com.escontrela.lastmove.application.training.memory;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;

/** Immutable answer target retained by a memory-game challenge. */
public record MemoryGamePiece(Square square, PieceType type, PieceColor color) {
  public MemoryGamePiece {
    Objects.requireNonNull(square, "square must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(color, "color must not be null");
  }
}
