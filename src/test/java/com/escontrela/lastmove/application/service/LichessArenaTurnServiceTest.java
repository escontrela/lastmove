package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LichessArenaTurnServiceTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test void ignoresAStateThatWouldMoveTheViewerBackwards() throws Exception {
    JsonNode current = json.readTree(
        "{\"status\":\"started\",\"moves\":\"e2e4 e7e5 g1f3 b8c6\"}");
    JsonNode stale = json.readTree("{\"status\":\"started\",\"moves\":\"e2e4 e7e5\"}");

    assertFalse(LichessArenaTurnService.shouldAcceptState(
        current.path("moves").asText(), current, stale));
  }

  @Test void terminalStateCannotBeOverwrittenByAnEqualLengthStartedState() throws Exception {
    JsonNode terminal = json.readTree(
        "{\"status\":\"resign\",\"winner\":\"white\",\"moves\":\"e2e4 e7e5\"}");
    JsonNode stale = json.readTree("{\"status\":\"started\",\"moves\":\"e2e4 e7e5\"}");
    JsonNode newer = json.readTree(
        "{\"status\":\"mate\",\"winner\":\"white\",\"moves\":\"e2e4 e7e5 g1f3\"}");

    assertFalse(LichessArenaTurnService.shouldAcceptState(
        terminal.path("moves").asText(), terminal, stale));
    assertTrue(LichessArenaTurnService.shouldAcceptState(
        terminal.path("moves").asText(), terminal, newer));
  }
}
