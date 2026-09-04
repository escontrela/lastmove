package com.escontrela.lastmove.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class MyGamesLayoutTest {
  @Test void presentsFiltersAndSavedGamesAsDashboardCards() throws Exception {
    String fxml;
    try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/fxml/my-games.fxml"))) {
      fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertTrue(fxml.contains("text=\"My Games\""));
    assertTrue(fxml.contains("styleClass=\"arena-bots-card, my-games-list-card\""));
    assertTrue(fxml.contains("text=\"Result\""));
    assertTrue(fxml.contains("text=\"Updated\""));
    assertTrue(fxml.contains("fx:id=\"gamesList\""));
    assertTrue(fxml.contains("fx:id=\"regexSearch\""));
    assertTrue(fxml.contains("fx:id=\"tagFilter\""));
  }
}
