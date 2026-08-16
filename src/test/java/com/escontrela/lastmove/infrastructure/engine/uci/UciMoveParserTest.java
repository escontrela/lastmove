package com.escontrela.lastmove.infrastructure.engine.uci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.domain.common.PieceType;
import org.junit.jupiter.api.Test;

class UciMoveParserTest {

  @Test
  void parsesACoordinateMove() {
    var move = UciMoveParser.parse("e2e4");

    assertEquals("e2", move.from().toAlgebraic());
    assertEquals("e4", move.to().toAlgebraic());
    assertTrue(move.promotion().isEmpty());
  }

  @Test
  void parsesAllSupportedPromotions() {
    assertEquals(PieceType.QUEEN, UciMoveParser.parse("e7e8q").promotion().orElseThrow());
    assertEquals(PieceType.ROOK, UciMoveParser.parse("e7e8r").promotion().orElseThrow());
    assertEquals(PieceType.BISHOP, UciMoveParser.parse("e7e8b").promotion().orElseThrow());
    assertEquals(PieceType.KNIGHT, UciMoveParser.parse("e7e8n").promotion().orElseThrow());
  }

  @Test
  void rejectsMalformedAndNullMoves() {
    assertThrows(ComputerEngineException.class, () -> UciMoveParser.parse("0000"));
    assertThrows(ComputerEngineException.class, () -> UciMoveParser.parse("bestmove e2e4"));
    assertThrows(ComputerEngineException.class, () -> UciMoveParser.parse(null));
  }
}
