package com.escontrela.lastmove.domain.study;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one persisted study owned by a player profile. */
public record StudyId(UUID value) {

  public StudyId {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** Creates a new random study identity. */
  public static StudyId random() {
    return new StudyId(UUID.randomUUID());
  }
}
