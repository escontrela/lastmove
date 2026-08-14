package com.escontrela.lastmove.domain.notation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.game.GameResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PgnGameTest {

  @Test
  void displayTitle_usesItsOwnHeadersWithSafeFallbacks() {
    assertEquals(
        "Ada vs. Grace – Test",
        new PgnGame(Map.of("White", "Ada", "Black", "Grace", "Event", "Test"), "", GameResult.UNKNOWN, null)
            .displayTitle());
    assertEquals(
        "? vs. ? – ?",
        new PgnGame(Map.of(), "", GameResult.UNKNOWN, null).displayTitle());
  }
}
