package com.escontrela.lastmove.ui.component.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegexSearchControlTest {

  @Test
  void acceptsAQueryAndAnEmptyQueryThatRestoresTheWholeList() {
    assertTrue(RegexSearchControl.compile("sicilian|french").isPresent());
    assertTrue(RegexSearchControl.compile("").isPresent());
  }

  @Test
  void rejectsInvalidRegularExpressionsWithoutThrowing() {
    assertFalse(RegexSearchControl.compile("[unclosed").isPresent());
  }
}
