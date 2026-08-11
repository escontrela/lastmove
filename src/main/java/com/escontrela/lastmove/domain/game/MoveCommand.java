package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import java.util.Optional;

/** A requested chess move, independent of the UI session that submitted it. */
public record MoveCommand(Square from, Square to, Optional<PieceType> promotion) {

  public MoveCommand {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    promotion = Objects.requireNonNull(promotion, "promotion must not be null");
  }
}
