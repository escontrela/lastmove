package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.Ply;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative tree of moves and variations for one analysis study.
 *
 * <p>The tree owns parent-child consistency, root ordering and node lookup. It does not own a
 * navigation cursor or a current board position; those responsibilities belong to {@link
 * AnalysisSession}.
 */
public final class AnalysisTree {

  private final Map<AnalysisNodeId, AnalysisNode> nodesById = new LinkedHashMap<>();
  private final Map<UUID, AnalysisNodeId> nodeIdsByPlyId = new LinkedHashMap<>();
  private final List<AnalysisNodeId> rootIds = new ArrayList<>();

  /** Adds a root variation beginning at the study's initial position. */
  public AnalysisNode addRoot(Ply ply) {
    return add(null, ply, AnalysisNodeId.random());
  }

  /** Adds a continuation to an existing parent node. */
  public AnalysisNode addChild(AnalysisNodeId parentId, Ply ply) {
    Objects.requireNonNull(parentId, "parentId must not be null");
    node(parentId);
    return add(parentId, ply, AnalysisNodeId.random());
  }

  /** Adds a root variation with a caller-supplied node identity, used when rehydrating trees. */
  public AnalysisNode addRoot(Ply ply, AnalysisNodeId nodeId) {
    return add(null, ply, Objects.requireNonNull(nodeId, "nodeId must not be null"));
  }

  /** Adds a continuation with a caller-supplied node identity, used when rehydrating trees. */
  public AnalysisNode addChild(AnalysisNodeId parentId, Ply ply, AnalysisNodeId nodeId) {
    Objects.requireNonNull(parentId, "parentId must not be null");
    node(parentId);
    return add(parentId, ply, Objects.requireNonNull(nodeId, "nodeId must not be null"));
  }

  /** Returns a node by identity. */
  public AnalysisNode node(AnalysisNodeId nodeId) {
    AnalysisNode node =
        nodesById.get(Objects.requireNonNull(nodeId, "nodeId must not be null"));
    if (node == null) {
      throw new NoSuchElementException("Unknown analysis node " + nodeId.value());
    }
    return node;
  }

  /** Finds a node by identity without failing when it does not belong to this tree. */
  public Optional<AnalysisNode> find(AnalysisNodeId nodeId) {
    return Optional.ofNullable(
        nodesById.get(Objects.requireNonNull(nodeId, "nodeId must not be null")));
  }

  /** Finds the structural node that wraps the supplied tree-neutral ply. */
  public Optional<AnalysisNode> findByPlyId(UUID plyId) {
    Objects.requireNonNull(plyId, "plyId must not be null");
    return Optional.ofNullable(nodeIdsByPlyId.get(plyId)).map(nodesById::get);
  }

  /** Returns root variations in preferred-continuation order. */
  public List<AnalysisNode> roots() {
    return rootIds.stream().map(nodesById::get).toList();
  }

  /** Returns the total number of moves retained across every variation. */
  public int size() {
    return nodesById.size();
  }

  /** Returns a node's continuations in preferred-continuation order. */
  public List<AnalysisNode> children(AnalysisNodeId parentId) {
    return node(parentId).continuationIds().stream().map(nodesById::get).toList();
  }

  /** Returns the path from the first move through the requested node. */
  public List<AnalysisNode> lineTo(AnalysisNodeId nodeId) {
    List<AnalysisNode> reverse = new ArrayList<>();
    AnalysisNode current = node(nodeId);
    while (current != null) {
      reverse.add(current);
      current = current.parentId().map(nodesById::get).orElse(null);
    }
    List<AnalysisNode> line = new ArrayList<>(reverse.size());
    for (int index = reverse.size() - 1; index >= 0; index--) {
      line.add(reverse.get(index));
    }
    return List.copyOf(line);
  }

  /** Atomically removes one node and its complete descendant branch. */
  public AnalysisBranchDeletion removeBranch(AnalysisNodeId nodeId) {
    AnalysisNode root = node(Objects.requireNonNull(nodeId, "nodeId must not be null"));
    List<AnalysisNodeId> removedIds = new ArrayList<>();
    collectBranchIds(root.id(), removedIds);
    root.parentId()
        .ifPresentOrElse(
            parentId -> node(parentId).removeContinuation(root.id()),
            () -> rootIds.remove(root.id()));
    for (AnalysisNodeId removedId : removedIds) {
      AnalysisNode removed = nodesById.remove(removedId);
      nodeIdsByPlyId.remove(removed.ply().id());
    }
    return new AnalysisBranchDeletion(root.id(), root.parentId(), removedIds);
  }

  private void collectBranchIds(AnalysisNodeId nodeId, List<AnalysisNodeId> target) {
    AnalysisNode node = node(nodeId);
    target.add(node.id());
    for (AnalysisNodeId childId : node.continuationIds()) {
      collectBranchIds(childId, target);
    }
  }

  private AnalysisNode add(AnalysisNodeId parentId, Ply ply, AnalysisNodeId nodeId) {
    Ply requiredPly = Objects.requireNonNull(ply, "ply must not be null");
    if (nodeIdsByPlyId.containsKey(requiredPly.id())) {
      throw new IllegalArgumentException("A ply can belong to only one analysis node");
    }
    if (nodesById.containsKey(nodeId)) {
      throw new IllegalArgumentException("An analysis node can be added only once");
    }
    AnalysisNode node = new AnalysisNode(nodeId, parentId, requiredPly);
    nodesById.put(node.id(), node);
    nodeIdsByPlyId.put(requiredPly.id(), node.id());
    if (parentId == null) {
      rootIds.add(node.id());
    } else {
      nodesById.get(parentId).addContinuation(node.id());
    }
    return node;
  }
}
