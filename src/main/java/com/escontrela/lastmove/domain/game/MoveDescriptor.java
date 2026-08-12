package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.Objects;
import java.util.Optional;

/** Immutable semantic description of an applied move, independent of the chess engine. */
public record MoveDescriptor(
    Square from,
    Square to,
    SanMove san,
    boolean capture,
    boolean castling,
    boolean enPassant,
    Optional<PieceType> promotion) {

  public MoveDescriptor {

    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(san, "san must not be null");
    
    promotion = Objects.requireNonNull(promotion, "promotion must not be null");
  }
}
