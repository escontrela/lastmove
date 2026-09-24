package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchTelemetryListener;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PonderSearchContextTest {
  private static final String START =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @Test
  void hitResumesAtTheNextDepthAndSeparatesPreviouslySearchedNodes() {
    ParallelRootSearch owner = new ParallelRootSearch(1);
    PonderSearchContext context = PonderSearchContext.prepare(owner, START, List.of(START),
        SearchLimits.bounded(Duration.ofSeconds(2), 2),
        SearchLimits.bounded(Duration.ofSeconds(2), 2), () -> false).orElseThrow();
    int priorDepth = context.completedContinuationDepth();
    List<String> actualHistory = List.of(START, context.expectedFen());

    assertTrue(new LegalMoveGenerator().generate(FenParser.parse(START))
        .contains(context.predictedOpponentMove()));
    assertTrue(context.matches(context.expectedFen(), actualHistory));
    var resumed = context.resume(context.expectedFen(), actualHistory,
        SearchLimits.depth(priorDepth + 1), () -> false, SearchTelemetryListener.NONE,
        1, null).orElseThrow();

    assertEquals(priorDepth + 1, resumed.depth());
    assertTrue(resumed.nodesBeforeThisSearch() >= context.predictionResult().nodes());
    assertTrue(resumed.nodesThisSearch() > 0);
    assertTrue(resumed.elapsedMillisBeforeThisSearch() >= context.predictionResult().elapsedMillis());
  }

  @Test
  void missCannotReuseContinuationFromAnotherPosition() {
    ParallelRootSearch owner = new ParallelRootSearch(1);
    PonderSearchContext context = PonderSearchContext.prepare(owner, START, List.of(START),
        SearchLimits.bounded(Duration.ofSeconds(2), 1),
        SearchLimits.bounded(Duration.ofSeconds(2), 1), () -> false).orElseThrow();

    assertFalse(context.matches(START, List.of(START)));
    assertTrue(context.resume(START, List.of(START), SearchLimits.depth(2), () -> false,
        SearchTelemetryListener.NONE, 1, null).isEmpty());
  }

  @Test
  void cancelledPredictionDoesNotProduceReusableState() {
    ParallelRootSearch owner = new ParallelRootSearch(1);
    assertTrue(PonderSearchContext.prepare(owner, START, List.of(START),
        SearchLimits.depth(4), SearchLimits.depth(8), () -> true).isEmpty());
  }

  @Test
  void completedPredictionCanBeValidatedEvenWhenNoContinuationDepthIsReusable() {
    ParallelRootSearch owner = new ParallelRootSearch(1);
    AtomicBoolean stopContinuation = new AtomicBoolean();
    PonderSearchContext context = PonderSearchContext.prepare(owner, START, List.of(START),
        SearchLimits.depth(1), SearchLimits.depth(8), stopContinuation::get,
        () -> stopContinuation.set(true)).orElseThrow();
    List<String> actualHistory = List.of(START, context.expectedFen());

    assertTrue(context.predictionMatches(context.expectedFen(), actualHistory));
    assertFalse(context.matches(context.expectedFen(), actualHistory));
    assertFalse(context.ready());
  }

  @Test
  void sameBoardWithDifferentOfficialHistoryOrFiftyMoveCounterIsNotAHit() {
    ParallelRootSearch owner = new ParallelRootSearch(1);
    PonderSearchContext context = PonderSearchContext.prepare(owner, START, List.of(START),
        SearchLimits.bounded(Duration.ofSeconds(2), 1),
        SearchLimits.bounded(Duration.ofSeconds(2), 1), () -> false).orElseThrow();
    String expected = context.expectedFen();
    List<String> officialHistory = List.of(START, expected);
    String[] fields = expected.split(" ");
    fields[4] = Integer.toString(Integer.parseInt(fields[4]) + 1);
    String changedHalfmoveClock = String.join(" ", fields);

    assertFalse(context.predictionMatches(expected, List.of(START, START, expected)));
    assertFalse(context.predictionMatches(changedHalfmoveClock,
        List.of(START, changedHalfmoveClock)));
    assertTrue(context.predictionMatches(expected, officialHistory));
  }

  @Test
  void specialRulePositionsKeepTheirExactPredictedReplyIdentity() {
    List<String> positions = List.of(
        "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", // castling rights
        "4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1", // en-passant target
        "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"); // promotion available

    for (String root : positions) {
      ParallelRootSearch owner = new ParallelRootSearch(1);
      PonderSearchContext context = PonderSearchContext.prepare(owner, root, List.of(root),
          SearchLimits.bounded(Duration.ofSeconds(2), 1),
          SearchLimits.bounded(Duration.ofSeconds(2), 1), () -> false).orElseThrow();

      assertTrue(context.predictionMatches(context.expectedFen(), List.of(root, context.expectedFen())));
      assertTrue(context.ready());
      context.cancel();
    }
  }
}
