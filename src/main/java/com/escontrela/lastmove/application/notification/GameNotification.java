package com.escontrela.lastmove.application.notification;

import com.escontrela.lastmove.domain.game.GameId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistent notification about a saved game owned by a local player. */
public record GameNotification(UUID id, GameId gameId, String kind, Instant createdAt, boolean unread) {
  public GameNotification { Objects.requireNonNull(id); Objects.requireNonNull(gameId); Objects.requireNonNull(kind); Objects.requireNonNull(createdAt); }
}
