package com.escontrela.lastmove.domain.tactics;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Objects;

/** A persisted exercise together with the ownership context needed by global training. */
public record TacticExerciseReference(
    PlayerId ownerId, TacticSuiteId suiteId, TacticExercise exercise) {
  public TacticExerciseReference {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Objects.requireNonNull(suiteId, "suiteId must not be null");
    Objects.requireNonNull(exercise, "exercise must not be null");
  }
}
