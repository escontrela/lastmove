package com.escontrela.lastmove.application.training.memory;

import java.util.List;
import java.util.Objects;

/** Position and the distinct pieces hidden for one memory-game round. */
public record MemoryGameChallenge(MemoryGamePosition position, List<MemoryGamePiece> hiddenPieces) {
  public MemoryGameChallenge {
    position = Objects.requireNonNull(position, "position must not be null");
    hiddenPieces = List.copyOf(Objects.requireNonNull(hiddenPieces, "hiddenPieces must not be null"));
    if (hiddenPieces.isEmpty()) throw new IllegalArgumentException("hiddenPieces must not be empty");
    if (hiddenPieces.stream().map(MemoryGamePiece::square).distinct().count() != hiddenPieces.size()) {
      throw new IllegalArgumentException("hidden pieces must occupy distinct squares");
    }
    if (hiddenPieces.stream().anyMatch(piece -> piece.type() == com.escontrela.lastmove.domain.common.PieceType.KING)) {
      throw new IllegalArgumentException("kings cannot be hidden");
    }
  }
}
