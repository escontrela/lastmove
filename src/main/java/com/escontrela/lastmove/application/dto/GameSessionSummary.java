package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.GameSessionOrigin;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.Objects;

/**
 * Immutable session-list item for UI consumers, without exposing the mutable session aggregate.
 */
public record GameSessionSummary(
    SessionId sessionId,
    String title,
    GameSessionOrigin origin,
    PositionSnapshot currentPosition) {

  public GameSessionSummary {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    title = Objects.requireNonNull(title, "title must not be null");
    origin = Objects.requireNonNull(origin, "origin must not be null");
    currentPosition = Objects.requireNonNull(currentPosition, "currentPosition must not be null");
  }
}
