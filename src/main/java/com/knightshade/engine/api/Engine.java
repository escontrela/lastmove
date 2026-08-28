package com.knightshade.engine.api;

import java.util.List;

/**
 * The Knightshade engine's public facade.
 *
 * <p>A caller supplies a FEN string plus limits and receives one move. This mirrors an in-process
 * UCI conversation ("position fen ..." + "go" => "bestmove ...") without any process boundary, and
 * is the only type LastMove's adapter depends on.
 */
public interface Engine {

  SearchResult search(String fen, SearchLimits limits, StopSignal stop);

  /** Searches with the official position history, including the current position. */
  default SearchResult search(
      String fen, List<String> positionHistory, SearchLimits limits, StopSignal stop) {
    return search(fen, limits, stop);
  }

  default SearchResult search(String fen, SearchLimits limits) {
    return search(fen, limits, StopSignal.never());
  }
}
