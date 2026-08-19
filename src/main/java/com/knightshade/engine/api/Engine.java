package com.knightshade.engine.api;

/**
 * The Knightshade engine's public facade.
 *
 * <p>A caller supplies a FEN string plus limits and receives one move. This mirrors an in-process
 * UCI conversation ("position fen ..." + "go" => "bestmove ...") without any process boundary, and
 * is the only type LastMove's adapter depends on.
 */
public interface Engine {

  SearchResult search(String fen, SearchLimits limits, StopSignal stop);

  default SearchResult search(String fen, SearchLimits limits) {
    return search(fen, limits, StopSignal.never());
  }
}
