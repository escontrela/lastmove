package com.escontrela.lastmove.ui.component.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

class BoardArrowTest {

  @Test
  void identifiesAVisualArrowByItsOriginAndDestination() {
    BoardArrow arrow = new BoardArrow(Square.of("g1"), Square.of("f3"));

    assertEquals(Square.of("g1"), arrow.from());
    assertEquals(Square.of("f3"), arrow.to());
    assertEquals(arrow, new BoardArrow(Square.of("g1"), Square.of("f3")));
  }

  @Test
  void rejectsAnArrowWithoutDirection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new BoardArrow(Square.of("e4"), Square.of("e4")));
  }
}
