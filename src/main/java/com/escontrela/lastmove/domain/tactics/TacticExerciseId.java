package com.escontrela.lastmove.domain.tactics;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one ordered tactical exercise in a suite. */
public record TacticExerciseId(UUID value) {

  public TacticExerciseId {
    Objects.requireNonNull(value, "value must not be null");
  }

  public static TacticExerciseId random() {
    return new TacticExerciseId(UUID.randomUUID());
  }
}
