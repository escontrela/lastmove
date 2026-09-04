package com.escontrela.lastmove.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class MemoryGameLayoutTest {
  @Test
  void exposesBoardClocksPhaseAndInScreenPiecePicker() throws Exception {
    String fxml = fxml();
    assertTrue(fxml.contains("<ChessBoardControl fx:id=\"chessBoard\""));
    assertTrue(fxml.contains("fx:id=\"globalClockLabel\""));
    assertTrue(fxml.contains("fx:id=\"memorizationClockLabel\""));
    assertTrue(fxml.contains("<MemoryPiecePickerControl fx:id=\"piecePicker\""));
    assertFalse(fxml.contains("fx:id=\"piecePalette\""));
    assertTrue(fxml.contains("accessibleText=\"Memory training chess board\""));
    assertFalse(fxml.contains("onAction=\"#submitAnswer\""));
    assertTrue(fxml.contains("ToolbarIconButton fx:id=\"resetButton\""));
    assertTrue(fxml.contains("progressive-danger-action-button"));
    assertTrue(fxml.contains("lightIconResource=\"/images/refresh_35dp_FFFFFF.png\""));
    assertTrue(fxml.contains("styleClass=\"status-text\" text=\"Memorize each position"));
  }

  @Test
  void exposesAHiddenResultPanelWithVerdictAndSingleRestart() throws Exception {
    String fxml = fxml();
    assertTrue(fxml.contains("fx:id=\"resultPanel\""));
    assertTrue(fxml.contains("fx:id=\"resultScoreLabel\""));
    assertTrue(fxml.contains("fx:id=\"resultVerdictLabel\""));
    assertTrue(fxml.contains("fx:id=\"resultDetailLabel\""));
    assertTrue(fxml.contains("fx:id=\"playAgainButton\""));
    assertTrue(fxml.contains("onAction=\"#playAgain\""));
    assertTrue(fxml.contains("styleClass=\"memory-result-card\""));
    assertTrue(fxml.contains("onAction=\"#resetSession\""));
    assertFalse(fxml.contains("onAction=\"#goHome\""));
  }

  @Test
  void placesTrainingCardAtTheEndOfTheSecondaryHomeGrid() throws Exception {
    try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/fxml/main-window.fxml"))) {
      String home = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      int secondaryGrid = home.indexOf("home-secondary-grid");
      int editor = home.indexOf("accessibleText=\"Position editor\"", secondaryGrid);
      int training = home.indexOf("accessibleText=\"Training Game\"", secondaryGrid);
      assertTrue(editor > secondaryGrid);
      assertTrue(training > editor);
    }
  }

  private static String fxml() throws Exception {
    try (var input = Objects.requireNonNull(MemoryGameLayoutTest.class.getResourceAsStream("/fxml/memory-game.fxml"))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
