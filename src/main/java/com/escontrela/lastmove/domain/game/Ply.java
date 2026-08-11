package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One half-move in a session tree, including the position it produces. */
public final class Ply {

  private final UUID id;
  private final UUID parentId;
  private final MoveDescriptor move;
  private final PositionSnapshot resultingPosition;
  private final int moveNumber;
  private final PieceColor movingColor;
  private final List<Ply> variations = new ArrayList<>();

  public Ply(
      UUID id,
      UUID parentId,
      MoveDescriptor move,
      PositionSnapshot resultingPosition,
      int moveNumber,
      PieceColor movingColor) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.parentId = parentId;
    this.move = Objects.requireNonNull(move, "move must not be null");
    this.resultingPosition = Objects.requireNonNull(resultingPosition, "resultingPosition must not be null");
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    this.moveNumber = moveNumber;
    this.movingColor = Objects.requireNonNull(movingColor, "movingColor must not be null");
  }

  public UUID id() { return id; }

  public Optional<UUID> parentId() { return Optional.ofNullable(parentId); }

  public MoveDescriptor move() { return move; }

  public PositionSnapshot resultingPosition() { return resultingPosition; }

  public int moveNumber() { return moveNumber; }

  public PieceColor movingColor() { return movingColor; }

  public List<Ply> variations() { return List.copyOf(variations); }

  void addVariation(Ply variation) {
    Objects.requireNonNull(variation, "variation must not be null");
    if (!id.equals(variation.parentId)) {
      throw new IllegalArgumentException("A variation must reference its parent ply");
    }
    variations.add(variation);
  }
}
