package com.escontrela.lastmove.domain.analysis;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of one move node inside an {@link AnalysisTree}. */
public record AnalysisNodeId(UUID value) {

  public AnalysisNodeId {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** Creates a new random node identity. */
  public static AnalysisNodeId random() {
    return new AnalysisNodeId(UUID.randomUUID());
  }
}
