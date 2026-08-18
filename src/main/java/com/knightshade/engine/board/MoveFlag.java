package com.knightshade.engine.board;

/**
 * Classifies the non-regular aspects of a move that require special make/unmake handling.
 *
 * <p>Captures and promotions are not encoded here: a capture is signalled by a non-null captured
 * piece on the move, and a promotion by a non-null promotion piece.
 */
public enum MoveFlag {
  NORMAL,
  DOUBLE_PAWN_PUSH,
  EN_PASSANT,
  KING_CASTLE,
  QUEEN_CASTLE
}
