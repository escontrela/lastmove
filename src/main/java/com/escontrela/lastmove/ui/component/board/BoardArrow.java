package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;

/** Immutable visual calculation arrow between two distinct board squares. */
public record BoardArrow(Square from, Square to) {

  public BoardArrow {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    if (from.equals(to)) {
      throw new IllegalArgumentException("an arrow requires two distinct squares");
    }
  }
}
