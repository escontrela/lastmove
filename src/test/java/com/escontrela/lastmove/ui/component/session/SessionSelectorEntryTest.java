package com.escontrela.lastmove.ui.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SessionSelectorEntryTest {

  @Test
  void normalizesTheReusableSessionTitle() {
    SessionSelectorEntry entry = new SessionSelectorEntry(0, "  Sicilian study  ");

    assertEquals("Sicilian study", entry.title());
    assertThrows(IllegalArgumentException.class, () -> new SessionSelectorEntry(1, "  "));
  }
}
