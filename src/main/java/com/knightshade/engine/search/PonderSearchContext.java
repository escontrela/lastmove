package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.api.SearchTelemetryListener;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Search state owned by one speculative continuation. The predictor is discarded after selecting
 * a legal reply; only the continuation root's worker, TT, evaluator cache and heuristics survive.
 */
public final class PonderSearchContext {
  private final ParallelRootSearch owner;
  private final ParallelRootSearch.ContinuationContext continuation;
  private final String predictedRootFen;
  private final String expectedFen;
  private final List<String> expectedHistory;
  private final Move predictedOpponentMove;
  private final SearchResult predictionResult;
  private final AtomicBoolean cancelled = new AtomicBoolean();
  private final AtomicBoolean transferred = new AtomicBoolean();
  private volatile SearchResult lastCompleteResult;

  private PonderSearchContext(ParallelRootSearch owner,
      ParallelRootSearch.ContinuationContext continuation, String predictedRootFen,
      String expectedFen, List<String> expectedHistory, Move predictedOpponentMove,
      SearchResult predictionResult) {
    this.owner = owner;
    this.continuation = continuation;
    this.predictedRootFen = predictedRootFen;
    this.expectedFen = expectedFen;
    this.expectedHistory = List.copyOf(expectedHistory);
    this.predictedOpponentMove = predictedOpponentMove;
    this.predictionResult = predictionResult;
    this.lastCompleteResult = continuation.lastCompleteResult();
  }

  /**
   * Searches a short opponent prediction, then searches our continuation from the resulting legal
   * position. A completed prediction is retained even if no continuation depth completed, so its
   * accuracy can still be validated when the real reply arrives.
   */
  public static Optional<PonderSearchContext> prepare(ParallelRootSearch owner, String rootFen,
      List<String> positionHistory, SearchLimits predictionLimits,
      SearchLimits continuationLimits, StopSignal stop) {
    return prepare(owner, rootFen, positionHistory, predictionLimits, continuationLimits, stop, () -> {});
  }

  public static Optional<PonderSearchContext> prepare(ParallelRootSearch owner, String rootFen,
      List<String> positionHistory, SearchLimits predictionLimits,
      SearchLimits continuationLimits, StopSignal stop, Runnable continuationStarted) {
    Objects.requireNonNull(owner, "owner must not be null");
    Objects.requireNonNull(rootFen, "rootFen must not be null");
    List<String> history = List.copyOf(Objects.requireNonNull(positionHistory,
        "positionHistory must not be null"));
    Objects.requireNonNull(predictionLimits, "predictionLimits must not be null");
    Objects.requireNonNull(continuationLimits, "continuationLimits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    Objects.requireNonNull(continuationStarted, "continuationStarted must not be null");

    Board root = FenParser.parse(rootFen);
    Map<Long, Integer> rootOccurrences = occurrences(history, rootFen);
    SearchResult prediction = owner.search(root, predictionLimits, stop, rootOccurrences);
    if (prediction.depth() < 1 || prediction.move() == null) {
      return Optional.empty();
    }

    List<Move> legal = owner.generateLegalMoves(root);
    if (!legal.contains(prediction.move())) return Optional.empty();
    Board expectedBoard = root.copy();
    expectedBoard.make(prediction.move());
    String expectedFen = expectedBoard.toFen();
    List<String> expectedHistory = normalizedHistory(history, rootFen);
    expectedHistory.add(expectedFen);
    Map<Long, Integer> continuationOccurrences = occurrences(expectedHistory, expectedFen);

    ParallelRootSearch.ContinuationContext continuation =
        owner.newContinuationContext(expectedBoard, continuationOccurrences);
    continuationStarted.run();
    SearchResult prepared = owner.searchContinuation(continuation, continuationLimits, stop);
    return Optional.of(new PonderSearchContext(owner, continuation, rootFen, expectedFen,
        expectedHistory, prediction.move(), prediction));
  }

  /** True only when the actual root and its full official position history exactly match. */
  public boolean matches(String actualFen, List<String> actualHistory) {
    return predictionMatches(actualFen, actualHistory) && continuation.completedDepth() >= 1;
  }

  /** Validates a completed prediction independently of whether continuation work is reusable. */
  public boolean predictionMatches(String actualFen, List<String> actualHistory) {
    if (cancelled.get() || transferred.get()) return false;
    if (!expectedFen.equals(actualFen)) return false;
    return expectedHistory.equals(normalizedHistory(
        List.copyOf(Objects.requireNonNull(actualHistory, "actualHistory must not be null")), actualFen));
  }

  /**
   * Transfers exclusive ownership after the speculative preparation task has returned. A mismatch
   * consumes nothing and returns empty so the caller can launch a normal search.
   */
  public Optional<SearchResult> resume(String actualFen, List<String> actualHistory,
      SearchLimits realTurnLimits, StopSignal stop, SearchTelemetryListener listener,
      int requestedWorkers, SearchTelemetryContext telemetryContext) {
    Objects.requireNonNull(realTurnLimits, "realTurnLimits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    if (transferred.get()) return Optional.empty();
    if (!matches(actualFen, actualHistory)) {
      cancel();
      return Optional.empty();
    }
    if (!transferred.compareAndSet(false, true)) return Optional.empty();
    SearchResult result = owner.searchContinuation(continuation, realTurnLimits,
        () -> cancelled.get() || stop.shouldStop(), listener, requestedWorkers, telemetryContext);
    SearchResult withPriorPrediction = new SearchResult(result.move(), result.score(), result.depth(),
        predictionResult.nodes() + result.nodes(), result.elapsedMillis(),
        predictionResult.nodes() + result.nodesBeforeThisSearch(), result.nodesThisSearch(),
        predictionResult.elapsedMillis() + result.elapsedMillisBeforeThisSearch());
    lastCompleteResult = withPriorPrediction;
    return Optional.of(withPriorPrediction);
  }

  public void cancel() {
    cancelled.set(true);
  }

  public boolean ready() {
    return !cancelled.get() && continuation.completedDepth() > 0;
  }

  public String predictedRootFen() { return predictedRootFen; }
  public String expectedFen() { return expectedFen; }
  public Move predictedOpponentMove() { return predictedOpponentMove; }
  public SearchResult predictionResult() { return predictionResult; }
  public SearchResult lastCompleteResult() { return lastCompleteResult; }
  public int completedContinuationDepth() { return continuation.completedDepth(); }

  private static Map<Long, Integer> occurrences(List<String> history, String currentFen) {
    Map<Long, Integer> result = new HashMap<>();
    for (String fen : normalizedHistory(history, currentFen)) {
      result.merge(FenParser.parse(fen).zobristKey(), 1, Integer::sum);
    }
    return result;
  }

  private static ArrayList<String> normalizedHistory(List<String> history, String currentFen) {
    ArrayList<String> copy = new ArrayList<>(history);
    if (copy.isEmpty() || !copy.getLast().equals(currentFen)) copy.add(currentFen);
    return copy;
  }
}
