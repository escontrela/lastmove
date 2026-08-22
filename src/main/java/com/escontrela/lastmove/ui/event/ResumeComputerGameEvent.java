package com.escontrela.lastmove.ui.event;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.Objects;

/** Requests opening a persisted Human-vs-Computer game without coupling screens. */
public record ResumeComputerGameEvent(GameId gameId) {
  public ResumeComputerGameEvent { Objects.requireNonNull(gameId, "gameId must not be null"); }
}
