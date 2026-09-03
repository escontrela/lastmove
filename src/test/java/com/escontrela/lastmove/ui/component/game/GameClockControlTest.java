package com.escontrela.lastmove.ui.component.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GameClockControlTest {

  @Test
  void calculatesAndClampsTheRemainingTimeFraction() {
    assertEquals(0.5, GameClockControl.remainingFraction(Duration.ofMinutes(5), Duration.ofMinutes(10)));
    assertEquals(1.0, GameClockControl.remainingFraction(Duration.ofMinutes(11), Duration.ofMinutes(10)));
    assertEquals(0.0, GameClockControl.remainingFraction(Duration.ZERO, Duration.ofMinutes(10)));
  }

}
