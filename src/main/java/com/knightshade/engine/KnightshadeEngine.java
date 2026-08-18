package com.knightshade.engine;

import com.knightshade.engine.api.Engine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.evaluation.MaterialEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.search.MinimaxSearch;
import com.knightshade.engine.search.Search;
import java.util.Objects;

/**
 * Default, dependency-free Knightshade engine assembly.
 *
 * <p>This class wires the v0 components together (legal move generation, material evaluation and
 * fixed-depth minimax) behind the public {@link Engine} contract. It depends only on the shared
 * value-object kernel from LastMove's domain and on the JDK.
 */
public final class KnightshadeEngine implements Engine {

  private final Search search;

  public KnightshadeEngine() {
    this(new LegalMoveGenerator(), new MaterialEvaluator());
  }

  KnightshadeEngine(MoveGenerator moveGenerator, Evaluator evaluator) {
    this.search = new MinimaxSearch(moveGenerator, evaluator);
  }

  @Override
  public SearchResult search(String fen, SearchLimits limits, StopSignal stop) {
    Objects.requireNonNull(fen, "fen must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    Board board = FenParser.parse(fen);
    return search.search(board, limits, stop);
  }
}
