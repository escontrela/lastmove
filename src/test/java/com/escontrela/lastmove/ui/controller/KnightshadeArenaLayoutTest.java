package com.escontrela.lastmove.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class KnightshadeArenaLayoutTest {
  @Test void keepsTournamentSectionAfterTheMainArenaColumnsAndWiresContextualMenus() throws Exception {
    String fxml;
    try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/fxml/knightshade-arena.fxml"))) {
      fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(fxml.indexOf("text=\"Recent activity\"") < fxml.indexOf("text=\"Challengers · Tournaments\""));
    assertTrue(fxml.contains("fx:id=\"tournamentContextMenu\""));
    assertTrue(fxml.contains("ArenaConsoleControl"));
    assertTrue(fxml.contains("fx:id=\"gamesConsole\""));
    assertTrue(fxml.contains("fx:id=\"applicationHeader\""));
    assertTrue(fxml.contains("GameTimelineControl"));
    assertTrue(fxml.contains("fx:id=\"blitzRatingLabel\""));
    assertTrue(!fxml.contains("fx:id=\"connectButton\""));
  }
}
