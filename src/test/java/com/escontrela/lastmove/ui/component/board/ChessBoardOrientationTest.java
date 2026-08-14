package com.escontrela.lastmove.ui.component.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void coordinateLabelsFollowTheVisiblePerspective() {
    assertEquals("a", ChessBoardSkin.fileLabelAt(0, false));
    assertEquals("h", ChessBoardSkin.fileLabelAt(7, false));
    assertEquals("8", ChessBoardSkin.rankLabelAt(0, false));
    assertEquals("1", ChessBoardSkin.rankLabelAt(7, false));

    assertEquals("h", ChessBoardSkin.fileLabelAt(0, true));
    assertEquals("a", ChessBoardSkin.fileLabelAt(7, true));
    assertEquals("1", ChessBoardSkin.rankLabelAt(0, true));
    assertEquals("8", ChessBoardSkin.rankLabelAt(7, true));
  }

  @Test
  void responsiveCoordinateGutterAlwaysStaysInsideTheControl() {
    assertResponsiveGeometry(240.0);
    assertResponsiveGeometry(480.0);
    assertResponsiveGeometry(720.0);
  }

  private void assertDisplayPosition(
      String algebraic, boolean flipped, int expectedColumn, int expectedRow) {
    Square square = Square.of(algebraic);
    assertEquals(expectedColumn, ChessBoardSkin.displayFile(square, flipped));
    assertEquals(expectedRow, ChessBoardSkin.displayRow(square, flipped));
  }

  private void assertResponsiveGeometry(double availableSide) {
    double gutter = ChessBoardSkin.coordinateGutter(availableSide);
    double boardSide = ChessBoardSkin.boardSideFor(availableSide, gutter);
    assertTrue(gutter >= 12.0 && gutter <= 22.0);
    assertEquals(0.0, boardSide % 8.0);
    assertTrue(boardSide + gutter <= availableSide);
  }
}
