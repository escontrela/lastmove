package com.escontrela.lastmove.ui.component.notation;

import java.util.Objects;
import java.util.UUID;

/**
 * UI-neutral description of one selectable ply rendered by {@link MoveNotationControl}.
 *
 * <p>The identifier is presentation-neutral: an analysis screen can use an analysis-node UUID and
 * a progressive game can use its ply UUID. The {@code activeLine} flag lets the skin distinguish
 * the currently chosen continuation from sibling variations without owning navigation state.
 */
public record MoveNotationEntry(
    UUID nodeId, int moveNumber, boolean whiteMove, String san, boolean activeLine) {

  public MoveNotationEntry {
    nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    san = Objects.requireNonNull(san, "san must not be null").trim();
    if (san.isEmpty()) {
      throw new IllegalArgumentException("san must not be blank");
    }
  }
}
