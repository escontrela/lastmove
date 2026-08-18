package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;

/**
 * Read-only view of one chess position.
 *
 * <p>The evaluation consumes this contract so it never depends on a particular board
 * representation. The mutable {@link Board} workspace is the v0 implementation; a future bitboard
 * representation can satisfy this same interface without touching evaluation or search.
 */
public interface Position {

  PieceColor sideToMove();

  CastlingRights castlingRights();

  /** Returns the en passant target square, or {@code null} when none is available. */
  Square enPassantTarget();

  int halfmoveClock();

  int fullmoveNumber();

  /** Returns the encoded piece at the given 0..63 square index, or {@link Piece#NONE}. */
  int pieceAt(int index);

  default int pieceAt(Square square) {
    return pieceAt(indexOf(square));
  }

  default boolean isEmpty(Square square) {
    return pieceAt(square) == Piece.NONE;
  }

  /** Converts a file/rank square to its 0..63 mailbox index (rank-major). */
  static int indexOf(Square square) {
    return square.getRank() * 8 + square.getFile();
  }

  /** Converts a 0..63 mailbox index back to a square. */
  static Square squareOf(int index) {
    return Square.of(index & 7, index >>> 3);
  }
}
