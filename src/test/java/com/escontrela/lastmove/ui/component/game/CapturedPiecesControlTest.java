package com.escontrela.lastmove.ui.component.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapturedPiecesControlTest {

  @Test
  void groupsEqualCapturedPiecesInMaterialOrder() {
    List<CapturedPiecesControl.CapturedPieceGroup> groups =
        CapturedPiecesControl.groups(
            List.of(
                piece("a2", PieceType.PAWN),
                piece("b1", PieceType.KNIGHT),
                piece("b2", PieceType.PAWN),
                piece("a1", PieceType.ROOK),
                piece("c2", PieceType.PAWN),
                piece("g1", PieceType.KNIGHT)));

    assertEquals(
        List.of(PieceType.ROOK, PieceType.KNIGHT, PieceType.PAWN),
        groups.stream().map(CapturedPiecesControl.CapturedPieceGroup::type).toList());
    assertEquals(
        List.of(1, 2, 3),
        groups.stream().map(CapturedPiecesControl.CapturedPieceGroup::count).toList());
    assertEquals(
        List.of(24.0, 32.0, 40.0),
        groups.stream().map(CapturedPiecesControl.CapturedPieceGroup::width).toList());
    assertEquals("3 black pawns", groups.getLast().accessibleText());
  }

  private PositionPiece piece(String square, PieceType type) {
    return new PositionPiece(Square.of(square), type, PieceColor.BLACK);
  }
}
