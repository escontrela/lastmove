package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable notation projection for one analysis session. */
public record AnalysisNotationTree(
    List<AnalysisNotationNode> roots, Optional<AnalysisNodeId> currentNodeId) {

  public AnalysisNotationTree {
    roots = List.copyOf(Objects.requireNonNull(roots, "roots must not be null"));
    currentNodeId = Objects.requireNonNull(currentNodeId, "currentNodeId must not be null");
  }
}
