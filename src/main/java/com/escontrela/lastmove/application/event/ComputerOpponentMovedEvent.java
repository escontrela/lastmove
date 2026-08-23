package com.escontrela.lastmove.application.event;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.Objects;

/** Signals that an unattended computer opponent has completed its move. */
public record ComputerOpponentMovedEvent(GameId gameId) {
  public ComputerOpponentMovedEvent { Objects.requireNonNull(gameId, "gameId must not be null"); }
}
