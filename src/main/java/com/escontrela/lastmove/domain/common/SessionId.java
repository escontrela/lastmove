package com.escontrela.lastmove.domain.common;

import java.util.Objects;
import java.util.UUID;

/** Identifies one independently navigable chess-board session. */
public record SessionId(UUID value) {

  public SessionId {
    Objects.requireNonNull(value, "session id must not be null");
  }

  public static SessionId random() {
    return new SessionId(UUID.randomUUID());
  }
}
