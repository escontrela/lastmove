package com.escontrela.lastmove.ui.component.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

class ChessBoardOrientationTest {

  @Test
  void mapsLogicalCornersForWhiteAndBlackPerspectives() {
    assertDisplayPosition("a1", false, 0, 7);
    assertDisplayPosition("h8", false, 7, 0);
    assertDisplayPosition("a1", true, 7, 0);
    assertDisplayPosition("h8", true, 0, 7);
  }

  @Test
  void convertsEveryVisualCellBackToItsLogicalSquare() {
    for (boolean flipped : new boolean[] {false, true}) {
      for (int file = 0; file < 8; file++) {
        for (int rank = 0; rank < 8; rank++) {
          Square square = Square.of(file, rank);
          assertEquals(
              square,
              ChessBoardSkin.logicalSquareAt(
                  ChessBoardSkin.displayFile(square, flipped),
                  ChessBoardSkin.displayRow(square, flipped),
                  flipped));
        }
      }
    }
  }

  private void assertDisplayPosition(
      String algebraic, boolean flipped, int expectedColumn, int expectedRow) {
    Square square = Square.of(algebraic);
    assertEquals(expectedColumn, ChessBoardSkin.displayFile(square, flipped));
    assertEquals(expectedRow, ChessBoardSkin.displayRow(square, flipped));
  }
}
