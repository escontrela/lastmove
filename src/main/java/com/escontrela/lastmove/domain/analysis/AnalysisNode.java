package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.Ply;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One structural node in an analysis tree.
 *
 * <p>The node wraps a tree-neutral {@link Ply} and owns only navigation relationships. Children
 * retain insertion order: the first child is the preferred continuation and later children are
 * alternative variations.
 */
public final class AnalysisNode {

  private final AnalysisNodeId id;
  private final AnalysisNodeId parentId;
  private final Ply ply;
  private final List<AnalysisNodeId> continuationIds = new ArrayList<>();

  AnalysisNode(AnalysisNodeId id, AnalysisNodeId parentId, Ply ply) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.parentId = parentId;
    this.ply = Objects.requireNonNull(ply, "ply must not be null");
  }

  public AnalysisNodeId id() {
    return id;
  }

  public Optional<AnalysisNodeId> parentId() {
    return Optional.ofNullable(parentId);
  }

  public Ply ply() {
    return ply;
  }

  public List<AnalysisNodeId> continuationIds() {
    return List.copyOf(continuationIds);
  }

  void addContinuation(AnalysisNodeId continuationId) {
    AnalysisNodeId child =
        Objects.requireNonNull(continuationId, "continuationId must not be null");
    if (continuationIds.contains(child)) {
      throw new IllegalArgumentException("The continuation is already registered");
    }
    continuationIds.add(child);
  }
}
