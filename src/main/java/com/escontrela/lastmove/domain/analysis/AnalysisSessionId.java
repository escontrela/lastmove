package com.escontrela.lastmove.domain.analysis;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one independently navigable chess-analysis study. */
public record AnalysisSessionId(UUID value) {

  public AnalysisSessionId {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** Creates a new random analysis-session identity. */
  public static AnalysisSessionId random() {
    return new AnalysisSessionId(UUID.randomUUID());
  }
}
