package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;

/** Searches a position for the best move within the supplied limits. */
public interface Search {

  SearchResult search(Board board, SearchLimits limits, StopSignal stop);
}
