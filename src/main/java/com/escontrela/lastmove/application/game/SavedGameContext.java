package com.escontrela.lastmove.application.game;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;
import java.util.Optional;

/** Metadata kept outside the common chess aggregate. */
public record SavedGameContext(
    GameType gameType, Optional<PlayerId> ownerPlayerId, Optional<ComputerGameConfiguration> computerConfiguration) {
  public SavedGameContext {
    gameType = Objects.requireNonNull(gameType, "gameType must not be null");
    ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
    computerConfiguration = Objects.requireNonNull(computerConfiguration, "computerConfiguration must not be null");
    if (gameType == GameType.HUMAN_VS_COMPUTER && computerConfiguration.isEmpty()) {
      throw new IllegalArgumentException("computer games require their configuration");
    }
  }
}
