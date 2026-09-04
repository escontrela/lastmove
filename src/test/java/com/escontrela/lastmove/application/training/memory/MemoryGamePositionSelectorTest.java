package com.escontrela.lastmove.application.training.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.training.memory.MemoryGameDifficulty;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemoryGamePositionSelectorTest {
  private static final String PLAYED_POSITION =
      "rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
  private static final String SECOND_PLAYED_POSITION =
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
  private static final String KINGS_ONLY = "8/8/8/8/8/8/4k3/4K3 w - - 0 1";

  @Test
  void selectsRequestedDistinctNonKingPiecesAndKeepsTheirIdentity() {
    MemoryGamePositionSelector selector = selectorWith(
        new MemoryGamePosition("game-a:4", PLAYED_POSITION));

    Optional<MemoryGameChallenge> challenge = selector.next(MemoryGameDifficulty.THREE_PIECES);

    assertTrue(challenge.isPresent());
    assertEquals(3, challenge.orElseThrow().hiddenPieces().size());
    assertEquals(3, challenge.orElseThrow().hiddenPieces().stream()
        .map(MemoryGamePiece::square).distinct().count());
    assertTrue(challenge.orElseThrow().hiddenPieces().stream()
        .noneMatch(piece -> piece.type() == PieceType.KING));
    assertEquals("game-a:4", challenge.orElseThrow().position().sourceId());
  }

  @Test
  void skipsCorruptAndInsufficientPositions() {
    MemoryGamePositionSelector selector = selectorWith(
        new MemoryGamePosition("broken", "not a fen"),
        new MemoryGamePosition("kings", KINGS_ONLY),
        new MemoryGamePosition("valid", PLAYED_POSITION));

    assertEquals("valid", selector.next(MemoryGameDifficulty.TWO_PIECES).orElseThrow()
        .position().sourceId());
  }

  @Test
  void doesNotRepeatPositionWhileAlternativesExistThenStartsANewCycle() {
    MemoryGamePositionSelector selector = selectorWith(
        new MemoryGamePosition("one", PLAYED_POSITION),
        new MemoryGamePosition("two", SECOND_PLAYED_POSITION));

    String first = selector.next(MemoryGameDifficulty.ONE_PIECE).orElseThrow().position().fen();
    String second = selector.next(MemoryGameDifficulty.ONE_PIECE).orElseThrow().position().fen();

    assertTrue(!first.equals(second));
    assertTrue(selector.next(MemoryGameDifficulty.ONE_PIECE).isPresent());

    selector.reset();
    assertTrue(selector.next(MemoryGameDifficulty.ONE_PIECE).isPresent());
  }

  @Test
  void returnsEmptyWhenNoPositionCanSatisfyDifficulty() {
    MemoryGamePositionSelector selector = selectorWith(new MemoryGamePosition("kings", KINGS_ONLY));

    assertTrue(selector.next(MemoryGameDifficulty.ONE_PIECE).isEmpty());
  }

  private static MemoryGamePositionSelector selectorWith(MemoryGamePosition... positions) {
    return new MemoryGamePositionSelector(
        () -> List.of(positions), new ChesspressoRulesEngine(), new Random(7));
  }
}
