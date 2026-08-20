package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.util.Optional;

/** Clipboard-safe payload used while dragging a palette piece onto an authoring board. */
public record BoardPieceDragPayload(PieceColor color, PieceType type) {
  private static final String PREFIX = "lastmove-board-piece:";

  public String encode() {
    return PREFIX + color.name() + ":" + type.name();
  }

  public static Optional<BoardPieceDragPayload> decode(String value) {
    if (value == null || !value.startsWith(PREFIX)) {
      return Optional.empty();
    }
    String[] fields = value.substring(PREFIX.length()).split(":", -1);
    if (fields.length != 2) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new BoardPieceDragPayload(PieceColor.valueOf(fields[0]), PieceType.valueOf(fields[1])));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
