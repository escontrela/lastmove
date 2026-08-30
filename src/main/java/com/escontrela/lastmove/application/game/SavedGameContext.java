package com.escontrela.lastmove.application.game;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;
import java.util.List;
import java.util.Optional;

/** Metadata kept outside the common chess aggregate. */
public record SavedGameContext(
    GameType gameType, Optional<PlayerId> ownerPlayerId,
    Optional<ComputerGameConfiguration> computerConfiguration,
    List<PlayerId> participantPlayerIds) {
  /** Source-compatible constructor for games that historically had one owner. */
  public SavedGameContext(GameType gameType, Optional<PlayerId> ownerPlayerId,
      Optional<ComputerGameConfiguration> computerConfiguration) {
    this(gameType, ownerPlayerId, computerConfiguration, ownerPlayerId.stream().toList());
  }
  public SavedGameContext {
    gameType = Objects.requireNonNull(gameType, "gameType must not be null");
    ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
    computerConfiguration = Objects.requireNonNull(computerConfiguration, "computerConfiguration must not be null");
    participantPlayerIds = List.copyOf(Objects.requireNonNull(participantPlayerIds, "participantPlayerIds must not be null"));
    if (participantPlayerIds.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("participantPlayerIds must not contain null");
    }
    if (ownerPlayerId.isPresent() && !participantPlayerIds.contains(ownerPlayerId.orElseThrow())) {
      throw new IllegalArgumentException("owner must be one of the game participants");
    }
    if (gameType == GameType.HUMAN_VS_COMPUTER && computerConfiguration.isEmpty()) {
      throw new IllegalArgumentException("computer games require their configuration");
    }
  }
}
