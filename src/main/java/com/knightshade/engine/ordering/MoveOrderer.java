package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import java.util.List;

/** Ranks moves so the search examines promising candidates first. */
public interface MoveOrderer {

  List<Move> order(Board board, List<Move> moves, OrderingContext context);

  List<Move> orderCaptures(Board board, List<Move> captures);
}
