package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.ordering.HistoryTable;
import com.knightshade.engine.ordering.KillerMoves;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mutable single-owner continuation state for one validated root and repetition history. */
public final class SearchContext {
  final IterativeDeepeningSearch owner;
  final Board root;
  final Map<Long, Integer> repetitions;
  final List<Move> rootMoves;
  final KillerMoves killers = new KillerMoves();
  final HistoryTable history = new HistoryTable();
  int completedDepth;
  int bestScore;
  Move bestMove;
  long totalNodes;
  long totalElapsedMillis;
  final AtomicBoolean inUse = new AtomicBoolean();
  SearchResult lastResult;

  SearchContext(IterativeDeepeningSearch owner, Board root, Map<Long, Integer> occurrences,
      List<Move> rootMoves) {
    this.owner = owner;
    this.root = root.copy();
    this.repetitions = new HashMap<>(occurrences);
    this.repetitions.putIfAbsent(this.root.zobristKey(), 1);
    this.rootMoves = List.copyOf(rootMoves);
    this.bestMove = rootMoves.isEmpty() ? null : rootMoves.getFirst();
    this.lastResult = new SearchResult(bestMove, 0, 0, 0, 0);
  }

  /** Last complete iteration, or a legal depth-zero fallback before the first iteration. */
  public SearchResult lastResult() {
    return lastResult;
  }

  public int completedDepth() {
    return completedDepth;
  }

  public long totalNodes() {
    return totalNodes;
  }

  public long totalElapsedMillis() {
    return totalElapsedMillis;
  }
}
