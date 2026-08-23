package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OpeningPracticeServiceTest {

  private final OpeningPracticeService service =
      new OpeningPracticeService(new ChessGameFactory(new ChesspressoRulesEngine()));

  @Test
  void resolvesSanLineIntoTheConfiguredMoveCommands() {
    ComputerGameConfiguration configured =
        service.configure(configuration(), "1. e4 e5 2. Nf3 Nc6", "90");

    var practice = configured.openingPractice().orElseThrow();

    assertEquals(90, practice.safetyThresholdCentipawns());
    assertEquals(4, practice.line().size());
    assertEquals("e2", practice.line().getFirst().from().toAlgebraic());
    assertEquals("c6", practice.line().getLast().to().toAlgebraic());
  }

  @Test
  void rejectsAnIllegalSanMoveBeforeTheGameStarts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> service.configure(configuration(), "e4 e4", "75"));

    assertTrue(exception.getMessage().contains("e4"));
  }

  @Test
  void keepsPracticeDisabledWhenNoLineWasEntered() {
    ComputerGameConfiguration configured = service.configure(configuration(), " ", "");

    assertEquals(java.util.Optional.empty(), configured.openingPractice());
  }

  private static ComputerGameConfiguration configuration() {
    return new ComputerGameConfiguration(
        "Player",
        PieceColor.WHITE,
        TimeControl.of(Duration.ofMinutes(10), Duration.ZERO),
        "knightshade",
        Duration.ofMillis(500));
  }
}
