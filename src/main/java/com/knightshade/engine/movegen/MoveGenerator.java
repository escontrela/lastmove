package com.knightshade.engine.movegen;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import java.util.List;

/** Generates legal moves for a position. */
public interface MoveGenerator {

  /** Returns every legal move available to the side to move in the given board. */
  List<Move> generate(Board board);

  /** Stops at the first legal move when the implementation supports it. */
  default boolean hasLegalMove(Board board) {
    return !generate(board).isEmpty();
  }

  /** Returns the legal captures and promotions only, used by quiescence search. */
  List<Move> generateCaptures(Board board);
}
