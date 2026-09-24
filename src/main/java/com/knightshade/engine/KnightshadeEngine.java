package com.knightshade.engine;

import com.knightshade.engine.api.Engine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.api.SearchTelemetryListener;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.search.IterativeDeepeningSearch;
import com.knightshade.engine.search.ParallelRootSearch;
import com.knightshade.engine.search.PonderSearchContext;
import com.knightshade.engine.search.Search;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default, dependency-free Knightshade engine assembly.
 *
 * <p>This class wires legal move generation, positional evaluation and parallel root PVS behind the
 * public {@link Engine} contract. Each request owns its workers and joins them before returning. It
 * depends only on the shared value-object kernel from LastMove's domain and on the JDK.
 */
public final class KnightshadeEngine implements Engine {

  private final Search search;
  private final boolean bitboards;

  public KnightshadeEngine() {
    this(Integer.getInteger(
        "knightshade.threads", Math.min(4, Runtime.getRuntime().availableProcessors())), false);
  }

  /** Total search participants, including the calling thread. One preserves sequential search. */
  public KnightshadeEngine(int threads) {
    this(threads, false);
  }

  public KnightshadeEngine(int threads, boolean bitboards) {
    this.bitboards = bitboards;
    this.search = new ParallelRootSearch(threads, bitboards);
  }

  KnightshadeEngine(MoveGenerator moveGenerator, Evaluator evaluator) {
    this.bitboards = false;
    this.search = new IterativeDeepeningSearch(moveGenerator, evaluator);
  }

  @Override
  public SearchResult search(String fen, SearchLimits limits, StopSignal stop) {
    return search(fen, List.of(), limits, stop);
  }

  @Override
  public SearchResult search(
      String fen, List<String> positionHistory, SearchLimits limits, StopSignal stop) {
    return search(fen, positionHistory, limits, stop, SearchTelemetryListener.NONE);
  }

  @Override
  public SearchResult search(
      String fen, List<String> positionHistory, SearchLimits limits, StopSignal stop,
      SearchTelemetryListener listener) {
    return search(fen, positionHistory, limits, stop, listener, null);
  }

  /** Searches with caller-supplied telemetry identity, or creates an unscoped one when absent. */
  public SearchResult search(
      String fen, List<String> positionHistory, SearchLimits limits, StopSignal stop,
      SearchTelemetryListener listener, SearchTelemetryContext telemetryContext) {
    Objects.requireNonNull(fen, "fen must not be null");
    Objects.requireNonNull(positionHistory, "positionHistory must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    Board board = FenParser.parse(fen);
    Map<Long, Integer> occurrences = new HashMap<>();
    for (String historicalFen : positionHistory) {
      long key = FenParser.parse(historicalFen).zobristKey();
      occurrences.merge(key, 1, Integer::sum);
    }
    occurrences.putIfAbsent(board.zobristKey(), 1);
    SearchTelemetryContext context = telemetryContext == null
        ? SearchTelemetryContext.unscoped(fen, board.sideToMove(), board.fullmoveNumber(), limits.toString(), positionHistory)
        : telemetryContext;
    return search.search(board, limits, stop, occurrences, listener, configuredThreads(), configuredThreads(), context);
  }

  /** Prepares one opponent prediction and a reusable, single-worker continuation context. */
  public Optional<PonderSearchContext> preparePonder(String fen, List<String> positionHistory,
      SearchLimits predictionLimits, SearchLimits continuationLimits, StopSignal stop) {
    return preparePonder(fen, positionHistory, predictionLimits, continuationLimits, stop, () -> {});
  }

  public Optional<PonderSearchContext> preparePonder(String fen, List<String> positionHistory,
      SearchLimits predictionLimits, SearchLimits continuationLimits, StopSignal stop,
      Runnable continuationStarted) {
    Objects.requireNonNull(fen, "fen must not be null");
    Objects.requireNonNull(positionHistory, "positionHistory must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    Objects.requireNonNull(continuationStarted, "continuationStarted must not be null");
    // Speculation uses one participant even when the ordinary search is parallel.
    ParallelRootSearch ponderOwner = new ParallelRootSearch(1, bitboards);
    return PonderSearchContext.prepare(ponderOwner, fen, positionHistory,
        predictionLimits, continuationLimits, stop, continuationStarted);
  }

  private int configuredThreads() {
    return search instanceof ParallelRootSearch parallel ? parallel.threads() : 1;
  }
}
