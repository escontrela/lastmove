package com.escontrela.lastmove.domain.tactics;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one persisted, player-owned tactical suite. */
public record TacticSuiteId(UUID value) {

  public TacticSuiteId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static TacticSuiteId random() {
    return new TacticSuiteId(UUID.randomUUID());
  }
}
