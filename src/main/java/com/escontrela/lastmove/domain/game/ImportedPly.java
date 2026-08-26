package com.escontrela.lastmove.domain.game;

import java.util.List;
import java.util.Objects;

/** One imported PGN move, its annotation, and every variation that continues from it. */
public record ImportedPly(
    MoveExecutionResult execution, String comment, List<ImportedPly> variations) {

  public ImportedPly {
    Objects.requireNonNull(execution, "execution must not be null");
    if (!execution.accepted()) {
      throw new IllegalArgumentException("An imported ply must contain an accepted execution");
    }
    comment = Objects.requireNonNullElse(comment, "").strip();
    variations = List.copyOf(Objects.requireNonNull(variations, "variations must not be null"));
  }
}
