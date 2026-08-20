package com.escontrela.lastmove.ui.component.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import org.junit.jupiter.api.Test;

class BoardPieceDragPayloadTest {
  @Test
  void roundTripsAValidPalettePayload() {
    BoardPieceDragPayload payload =
        new BoardPieceDragPayload(PieceColor.BLACK, PieceType.KNIGHT);

    assertEquals(payload, BoardPieceDragPayload.decode(payload.encode()).orElseThrow());
    assertTrue(BoardPieceDragPayload.decode("unrelated clipboard text").isEmpty());
  }
}
