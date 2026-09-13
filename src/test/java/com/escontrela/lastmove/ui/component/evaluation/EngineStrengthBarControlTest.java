package com.escontrela.lastmove.ui.component.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EngineStrengthBarControlTest {

  @Test
  void convertsWhitePerspectiveScoresIntoAWhiteShare() {
    assertEquals(0.5, EngineStrengthBarControl.whiteShare("+0.00"), 0.0001);
    assertEquals(0.95, EngineStrengthBarControl.whiteShare("+10.00"), 0.0001);
    assertEquals(0.05, EngineStrengthBarControl.whiteShare("-10.00"), 0.0001);
  }

  @Test
  void treatsMateScoresAsDecisiveAndUnknownScoresAsBalanced() {
    assertEquals(1.0, EngineStrengthBarControl.whiteShare("#3"));
    assertEquals(0.0, EngineStrengthBarControl.whiteShare("-#2"));
    assertEquals(0.5, EngineStrengthBarControl.whiteShare("not available"));
  }
}
