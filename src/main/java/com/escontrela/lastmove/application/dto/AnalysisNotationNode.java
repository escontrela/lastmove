package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.game.Ply;
import java.util.List;
import java.util.Objects;

/**
 * Recursive application projection of one selectable analysis move and all its continuations.
 *
 * <p>{@code mainContinuation} identifies the first recorded child at its branch point, while
 * {@code activeLine} marks the route currently selected for keyboard navigation. Neither flag
 * changes chess state; they allow presentation controls to render the tree without querying the
 * aggregate node by node.
 */
public record AnalysisNotationNode(
    AnalysisNodeId nodeId,
    Ply ply,
    List<AnalysisNotationNode> continuations,
    boolean mainContinuation,
    boolean activeLine,
    boolean current) {

  public AnalysisNotationNode {
    Objects.requireNonNull(nodeId, "nodeId must not be null");
    Objects.requireNonNull(ply, "ply must not be null");
    continuations =
        List.copyOf(Objects.requireNonNull(continuations, "continuations must not be null"));
  }
}
