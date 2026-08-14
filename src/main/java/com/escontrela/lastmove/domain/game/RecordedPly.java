package com.escontrela.lastmove.domain.game;

import java.util.Objects;

/** One official played ply together with the clock state immediately before and after it. */
public record RecordedPly(
    Ply ply, GameClockSnapshot clockBeforeMove, GameClockSnapshot clockAfterMove) {

  public RecordedPly {
    Objects.requireNonNull(ply, "ply must not be null");
    Objects.requireNonNull(clockBeforeMove, "clockBeforeMove must not be null");
    Objects.requireNonNull(clockAfterMove, "clockAfterMove must not be null");
  }
}
