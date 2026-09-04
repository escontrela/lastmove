package com.escontrela.lastmove.domain.training.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryGameDifficultyTest {

  @Test
  void usesOnePieceUntilNinetySeconds() {
    assertEquals(MemoryGameDifficulty.ONE_PIECE, MemoryGameDifficulty.at(Duration.ZERO));
    assertEquals(
        MemoryGameDifficulty.ONE_PIECE,
        MemoryGameDifficulty.at(Duration.ofSeconds(90).minusNanos(1)));
  }

  @Test
  void usesTwoPiecesFromNinetyUntilOneHundredFiftySeconds() {
    assertEquals(MemoryGameDifficulty.TWO_PIECES, MemoryGameDifficulty.at(Duration.ofSeconds(90)));
    assertEquals(
        MemoryGameDifficulty.TWO_PIECES,
        MemoryGameDifficulty.at(Duration.ofSeconds(150).minusNanos(1)));
  }

  @Test
  void usesThreePiecesFromOneHundredFiftySeconds() {
    assertEquals(MemoryGameDifficulty.THREE_PIECES, MemoryGameDifficulty.at(Duration.ofSeconds(150)));
    assertEquals(MemoryGameDifficulty.THREE_PIECES, MemoryGameDifficulty.at(Duration.ofMinutes(3)));
  }

  @Test
  void rejectsNegativeElapsedTime() {
    assertThrows(
        IllegalArgumentException.class, () -> MemoryGameDifficulty.at(Duration.ofNanos(-1)));
  }
}
