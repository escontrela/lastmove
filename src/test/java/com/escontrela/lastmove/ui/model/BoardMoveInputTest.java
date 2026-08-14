package com.escontrela.lastmove.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

class BoardMoveInputTest {

  @Test
  void completesAPendingBoardGestureWithTheSelectedPromotionPiece() {
    BoardMoveInput pending = BoardMoveInput.from(Square.of("a7"), Square.of("a8"));

    BoardMoveInput completed = pending.withPromotion(PieceType.KNIGHT);

    assertEquals(Square.of("a7"), completed.fromSquare());
    assertEquals(Square.of("a8"), completed.toSquare());
    assertTrue(completed.promotionPiece().isPresent());
    assertEquals(PieceType.KNIGHT, completed.promotionPiece().orElseThrow());
  }
}
