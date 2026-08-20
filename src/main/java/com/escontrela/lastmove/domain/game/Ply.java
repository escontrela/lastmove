package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, tree-neutral half-move and the complete position it produces.
 *
 * <p>A ply deliberately has no parent or children. Progressive games order plies in a list, while
 * analysis studies place them inside analysis nodes that own variation relationships.
 */
public final class Ply {

  private final UUID id;
  private final MoveDescriptor move;
  private final PositionSnapshot resultingPosition;
  private final int moveNumber;
  private final PieceColor movingColor;
  private final Optional<PositionPiece> capturedPiece;

  public Ply(
      UUID id,
      MoveDescriptor move,
      PositionSnapshot resultingPosition,
      int moveNumber,
      PieceColor movingColor) {
    this(id, move, resultingPosition, moveNumber, movingColor, Optional.empty());
  }

  public Ply(
      UUID id,
      MoveDescriptor move,
      PositionSnapshot resultingPosition,
      int moveNumber,
      PieceColor movingColor,
      Optional<PositionPiece> capturedPiece) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.move = Objects.requireNonNull(move, "move must not be null");
    this.resultingPosition = Objects.requireNonNull(resultingPosition, "resultingPosition must not be null");
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    this.moveNumber = moveNumber;
    this.movingColor = Objects.requireNonNull(movingColor, "movingColor must not be null");
    this.capturedPiece = Objects.requireNonNull(capturedPiece, "capturedPiece must not be null");
  }

  public UUID id() { return id; }

  public MoveDescriptor move() { return move; }

  public PositionSnapshot resultingPosition() { return resultingPosition; }

  public int moveNumber() { return moveNumber; }

  public PieceColor movingColor() { return movingColor; }

  /** Returns the actual piece removed by this ply, including en-passant and promoted pieces. */
  public Optional<PositionPiece> capturedPiece() { return capturedPiece; }
}
