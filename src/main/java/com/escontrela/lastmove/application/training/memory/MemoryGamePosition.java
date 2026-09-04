package com.escontrela.lastmove.application.training.memory;

import java.util.Objects;

/** Application DTO for a position reached by a played move. */
public record MemoryGamePosition(String sourceId, String fen) {
  public MemoryGamePosition {
    sourceId = requireText(sourceId, "sourceId");
    fen = requireText(fen, "fen");
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    return value;
  }
}
