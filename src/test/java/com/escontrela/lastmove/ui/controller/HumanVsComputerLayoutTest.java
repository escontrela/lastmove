package com.escontrela.lastmove.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class HumanVsComputerLayoutTest {

  @Test
  void usesReusableClocksAndKeepsNavigationBelowNotation() throws Exception {
    String fxml;
    try (var input = Objects.requireNonNull(
        getClass().getResourceAsStream("/fxml/progressive-game.fxml"))) {
      fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertTrue(fxml.contains("<GameClockControl fx:id=\"opponentClock\""));
    assertTrue(fxml.contains("<GameClockControl fx:id=\"humanClock\""));
    assertTrue(fxml.indexOf("fx:id=\"moveNotation\"") < fxml.indexOf("fx:id=\"firstMoveButton\""));
    assertFalse(fxml.contains("text=\"Moves\""));
    assertTrue(fxml.contains("styleClass=\"arena-action-button, progressive-game-action\""));
  }
}
