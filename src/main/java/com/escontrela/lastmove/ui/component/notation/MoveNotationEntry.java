package com.escontrela.lastmove.ui.component.notation;

import java.util.Objects;

/**
 * UI-neutral description of one selectable ply rendered by {@link MoveNotationControl}.
 *
 * <p>The entry deliberately uses a line-local index instead of an analysis-session identifier, so
 * the same control can render an analysis tree's selected line or a progressive game's history.
 */
public record MoveNotationEntry(
    int plyIndex, int moveNumber, boolean whiteMove, String san) {

  public MoveNotationEntry {
    if (plyIndex < 0) {
      throw new IllegalArgumentException("plyIndex must not be negative");
    }
    if (moveNumber < 1) {
      throw new IllegalArgumentException("moveNumber must be at least one");
    }
    san = Objects.requireNonNull(san, "san must not be null").trim();
    if (san.isEmpty()) {
      throw new IllegalArgumentException("san must not be blank");
    }
  }
}
