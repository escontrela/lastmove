package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, engine-independent board state intended for application and UI consumers. */
public record PositionSnapshot(
    List<PositionPiece> pieces,
    PieceColor activeColor,
    Optional<MoveDescriptor> lastMove,
    boolean check,
    boolean mate) {

  public PositionSnapshot {
    pieces = List.copyOf(Objects.requireNonNull(pieces, "pieces must not be null"));
    Objects.requireNonNull(activeColor, "activeColor must not be null");
    lastMove = Objects.requireNonNull(lastMove, "lastMove must not be null");
  }
}
