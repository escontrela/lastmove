package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.game.Ply;
import java.util.Objects;

/** Immutable application view of one selectable move node in an analysis tree. */
public record AnalysisNodeSummary(
    AnalysisNodeId nodeId, Ply ply, int continuationCount) {

  public AnalysisNodeSummary {
    Objects.requireNonNull(nodeId, "nodeId must not be null");
    Objects.requireNonNull(ply, "ply must not be null");
    if (continuationCount < 0) {
      throw new IllegalArgumentException("continuationCount must not be negative");
    }
  }
}
