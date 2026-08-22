package com.escontrela.lastmove.application.game;

import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GameResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Lightweight row used by game history, notifications and active-game views. */
public record SavedGameSummary(
    GameId gameId,
    GameType gameType,
    String whiteName,
    String blackName,
    boolean finished,
    Optional<GameResult> result,
    int movesCount,
    Instant updatedAt) {
  public SavedGameSummary {
    gameId = Objects.requireNonNull(gameId, "gameId must not be null");
    gameType = Objects.requireNonNull(gameType, "gameType must not be null");
    whiteName = Objects.requireNonNull(whiteName, "whiteName must not be null");
    blackName = Objects.requireNonNull(blackName, "blackName must not be null");
    result = Objects.requireNonNull(result, "result must not be null");
    updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
