package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import java.util.Map;

/** Searches a position for the best move within the supplied limits. */
public interface Search {

  SearchResult search(Board board, SearchLimits limits, StopSignal stop);

  default SearchResult search(
      Board board,
      SearchLimits limits,
      StopSignal stop,
      Map<Long, Integer> positionOccurrences) {
    return search(board, limits, stop);
  }
}
