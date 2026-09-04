package com.escontrela.lastmove.domain.training.memory;

import java.time.Duration;
import java.util.Objects;

/** Difficulty selected when a training round enters its guessing phase. */
public enum MemoryGameDifficulty {
  ONE_PIECE(1),
  TWO_PIECES(2),
  THREE_PIECES(3);

  private static final Duration TWO_PIECES_FROM = Duration.ofSeconds(90);
  private static final Duration THREE_PIECES_FROM = Duration.ofSeconds(150);

  private final int hiddenPieceCount;

  MemoryGameDifficulty(int hiddenPieceCount) {
    this.hiddenPieceCount = hiddenPieceCount;
  }

  public int hiddenPieceCount() {
    return hiddenPieceCount;
  }

  /** Resolves difficulty from elapsed session time, including the exact boundary instants. */
  public static MemoryGameDifficulty at(Duration elapsed) {
    Duration required = Objects.requireNonNull(elapsed, "elapsed must not be null");
    if (required.isNegative()) {
      throw new IllegalArgumentException("elapsed must not be negative");
    }
    if (required.compareTo(THREE_PIECES_FROM) >= 0) {
      return THREE_PIECES;
    }
    if (required.compareTo(TWO_PIECES_FROM) >= 0) {
      return TWO_PIECES;
    }
    return ONE_PIECE;
  }
}
