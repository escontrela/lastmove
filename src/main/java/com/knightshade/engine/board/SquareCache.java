package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.Square;

/** Engine-local sharing of immutable square values; the backing array never escapes. */
final class SquareCache {
  private static final Square[] SQUARES = new Square[64];

  static {
    for (int index = 0; index < 64; index++) {
      SQUARES[index] = Square.of(index & 7, index >>> 3);
    }
  }

  private SquareCache() {}

  static Square at(int index) {
    return SQUARES[index];
  }
}
