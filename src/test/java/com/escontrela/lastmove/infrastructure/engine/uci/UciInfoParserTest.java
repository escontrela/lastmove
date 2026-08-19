package com.escontrela.lastmove.infrastructure.engine.uci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UciInfoParserTest {

  @Test
  void parsesACentipawnScore() {
    var score =
        UciInfoParser.parseScore(
            "info depth 12 seldepth 14 multipv 1 score cp 34 lowerbound nodes 1234 pv e2e4");

    assertTrue(score.isPresent());
    assertFalse(score.orElseThrow().isMate());
    assertEquals(34, score.orElseThrow().value());
  }

  @Test
  void parsesAMateScore() {
    var score = UciInfoParser.parseScore("info depth 5 score mate 3 nodes 99 pv e2e4");

    assertTrue(score.isPresent());
    assertTrue(score.orElseThrow().isMate());
    assertEquals(3, score.orElseThrow().value());
  }

  @Test
  void returnsNoScoreForLinesWithoutAnEvaluation() {
    assertTrue(UciInfoParser.parseScore("info depth 4 nodes 100").isEmpty());
    assertTrue(UciInfoParser.parseScore("bestmove e2e4").isEmpty());
  }

  @Test
  void parsesTheSearchDepthAndIgnoresSeldepth() {
    assertEquals(
        Integer.valueOf(12),
        UciInfoParser.parseDepth("info depth 12 seldepth 16 score cp 34").orElseThrow());
    assertTrue(UciInfoParser.parseDepth("bestmove e2e4").isEmpty());
  }
}
