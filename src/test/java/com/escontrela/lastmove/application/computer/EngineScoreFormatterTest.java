package com.escontrela.lastmove.application.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.common.PieceColor;
import org.junit.jupiter.api.Test;

class EngineScoreFormatterTest {

  @Test
  void formatsCentipawnsFromWhitesPerspective() {
    assertEquals("+0.35", EngineScoreFormatter.format(EngineScore.centipawns(35), PieceColor.WHITE));
    assertEquals("-0.35", EngineScoreFormatter.format(EngineScore.centipawns(35), PieceColor.BLACK));
    assertEquals("-1.20", EngineScoreFormatter.format(EngineScore.centipawns(-120), PieceColor.WHITE));
    assertEquals("+1.20", EngineScoreFormatter.format(EngineScore.centipawns(-120), PieceColor.BLACK));
    assertEquals("+0.00", EngineScoreFormatter.format(EngineScore.centipawns(0), PieceColor.WHITE));
  }

  @Test
  void formatsMateFromWhitesPerspective() {
    assertEquals("#1", EngineScoreFormatter.format(EngineScore.mateIn(1), PieceColor.WHITE));
    assertEquals("-#1", EngineScoreFormatter.format(EngineScore.mateIn(1), PieceColor.BLACK));
    assertEquals("-#3", EngineScoreFormatter.format(EngineScore.mateIn(-5), PieceColor.WHITE));
    assertEquals("#3", EngineScoreFormatter.format(EngineScore.mateIn(-5), PieceColor.BLACK));
  }
}
