package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;

/** A single piece placed on a square in a renderable position snapshot. */
public record PositionPiece(Square square, PieceType type, PieceColor color) {

  public PositionPiece {
    Objects.requireNonNull(square, "square must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(color, "color must not be null");
  }
}
