package com.escontrela.lastmove.domain.analysis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of atomically removing one move and every continuation below it. */
public record AnalysisBranchDeletion(
    AnalysisNodeId deletedRootId,
    Optional<AnalysisNodeId> parentNodeId,
    List<AnalysisNodeId> removedNodeIds) {

  public AnalysisBranchDeletion {
    Objects.requireNonNull(deletedRootId, "deletedRootId must not be null");
    parentNodeId = Objects.requireNonNull(parentNodeId, "parentNodeId must not be null");
    removedNodeIds =
        List.copyOf(
            Objects.requireNonNull(removedNodeIds, "removedNodeIds must not be null"));
    if (removedNodeIds.isEmpty() || !removedNodeIds.contains(deletedRootId)) {
      throw new IllegalArgumentException("removedNodeIds must contain the deleted root");
    }
  }

  public boolean variation() {
    return removedNodeIds.size() > 1;
  }
}
