package com.escontrela.lastmove.ui.component.notation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recursive presentation node consumed by {@link MoveNotationControl}.
 *
 * <p>Each node contains one selectable ply and every legal continuation retained by the owning
 * workflow. The first continuation is rendered as the preferred line and later continuations as
 * indented variations; the control never changes domain ordering itself.
 */
public record MoveNotationNode(MoveNotationEntry entry, List<MoveNotationNode> continuations) {

  public MoveNotationNode {
    entry = Objects.requireNonNull(entry, "entry must not be null");
    continuations =
        List.copyOf(Objects.requireNonNull(continuations, "continuations must not be null"));
    if (continuations.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("continuations must not contain null");
    }
  }

  /** Returns whether the recursive presentation tree still contains an identifier. */
  public static boolean contains(List<MoveNotationNode> nodes, UUID identifier) {
    Objects.requireNonNull(nodes, "nodes must not be null");
    Objects.requireNonNull(identifier, "identifier must not be null");
    for (MoveNotationNode node : nodes) {
      if (node.entry().nodeId().equals(identifier)
          || contains(node.continuations(), identifier)) {
        return true;
      }
    }
    return false;
  }
}
