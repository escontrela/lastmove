package com.escontrela.lastmove.application.event;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.Objects;

/** Signals UI observers that a live computer game has reached a terminal result. */
public record ComputerGameFinishedEvent(GameId gameId) {
  public ComputerGameFinishedEvent { Objects.requireNonNull(gameId, "gameId must not be null"); }
}
