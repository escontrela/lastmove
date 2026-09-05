package com.escontrela.lastmove.application.training.storm;

import java.util.Objects;

/** Result of applying one move or hint during a Storm puzzle. */
public record StormGameMoveOutcome(StormGameSnapshot snapshot, StormGameFeedback feedback) {
  public StormGameMoveOutcome {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    Objects.requireNonNull(feedback, "feedback must not be null");
  }
}
