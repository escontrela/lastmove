package com.escontrela.lastmove.domain.game;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a progressive, linear chess game. */
public record GameId(UUID value) {

  public GameId {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** Creates a new random game identity. */
  public static GameId random() {
    return new GameId(UUID.randomUUID());
  }
}
