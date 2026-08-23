package com.escontrela.lastmove.ui.component.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegexSearchFilterTest {

  @Test
  void matchesAnyExplicitlyProvidedGridField() {
    Pattern pattern = Pattern.compile("(Sicilian|finished)", Pattern.CASE_INSENSITIVE);

    assertTrue(RegexSearchFilter.matches(pattern, "Casual game", "Finished", "Black wins"));
    assertTrue(RegexSearchFilter.matches(pattern, "Sicilian repertoire", "12 chapters"));
    assertFalse(RegexSearchFilter.matches(pattern, "French repertoire", "In progress"));
  }
}
