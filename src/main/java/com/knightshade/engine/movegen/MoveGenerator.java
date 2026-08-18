package com.knightshade.engine.movegen;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import java.util.List;

/** Generates legal moves for a position. */
public interface MoveGenerator {

  /** Returns every legal move available to the side to move in the given board. */
  List<Move> generate(Board board);
}
