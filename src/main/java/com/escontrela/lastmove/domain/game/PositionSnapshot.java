package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, engine-independent complete state of one chess position.
 *
 * <p>It contains every value required to reconstruct legal move generation, while remaining safe
 * for application and UI consumers to render or retain.
 */
public record PositionSnapshot(
    List<PositionPiece> pieces,
    PieceColor activeColor,
    CastlingRights castlingRights,
    Optional<Square> enPassantTarget,
    int halfmoveClock,
    int fullmoveNumber,
    Optional<MoveDescriptor> lastMove,
    boolean check,
    boolean mate,
    boolean stalemate) {

  public PositionSnapshot {
    pieces = List.copyOf(Objects.requireNonNull(pieces, "pieces must not be null"));
    Objects.requireNonNull(activeColor, "activeColor must not be null");
    Objects.requireNonNull(castlingRights, "castlingRights must not be null");
    enPassantTarget = Objects.requireNonNull(enPassantTarget, "enPassantTarget must not be null");
    if (halfmoveClock < 0) {
      throw new IllegalArgumentException("halfmoveClock must not be negative");
    }
    if (fullmoveNumber < 1) {
      throw new IllegalArgumentException("fullmoveNumber must be at least one");
    }
    lastMove = Objects.requireNonNull(lastMove, "lastMove must not be null");
    if (mate && stalemate) {
      throw new IllegalArgumentException("A position cannot be both mate and stalemate");
    }
  }

  /** Compatibility constructor while producers are migrated to the complete position state. */
  public PositionSnapshot(
      List<PositionPiece> pieces,
      PieceColor activeColor,
      Optional<MoveDescriptor> lastMove,
      boolean check,
      boolean mate) {
    this(pieces, activeColor, CastlingRights.none(), Optional.empty(), 0, 1, lastMove, check, mate, false);
  }
}
