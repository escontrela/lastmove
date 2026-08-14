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
    return add(null, ply);
  }

  /** Adds a continuation to an existing parent node. */
  public AnalysisNode addChild(AnalysisNodeId parentId, Ply ply) {
    Objects.requireNonNull(parentId, "parentId must not be null");
    node(parentId);
    return add(parentId, ply);
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

  private AnalysisNode add(AnalysisNodeId parentId, Ply ply) {
    Ply requiredPly = Objects.requireNonNull(ply, "ply must not be null");
    if (nodeIdsByPlyId.containsKey(requiredPly.id())) {
      throw new IllegalArgumentException("A ply can belong to only one analysis node");
    }
    AnalysisNode node = new AnalysisNode(AnalysisNodeId.random(), parentId, requiredPly);
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
