package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.KnightshadeEngine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.evaluation.PositionalEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class ParallelRootSearchTest {
  private static final String START =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @Test
  void oneThreadPreservesSequentialResults() {
    for (String fen : new String[] {START,
        "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3"}) {
      var expected = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PositionalEvaluator())
          .search(FenParser.parse(fen), SearchLimits.depth(4), StopSignal.never());
      var actual = new KnightshadeEngine(1).search(fen, SearchLimits.depth(4), StopSignal.never());
      assertEquals(expected.move(), actual.move());
      assertEquals(expected.score(), actual.score());
      assertEquals(expected.depth(), actual.depth());
      assertEquals(expected.nodes(), actual.nodes());
    }
  }

  @Test
  void shallowParallelScoresAgreeWithSequentialSearch() {
    for (int threads : new int[] {2, 4}) {
      var expected = new KnightshadeEngine(1).search(START, SearchLimits.depth(3));
      var actual = new KnightshadeEngine(threads).search(START, SearchLimits.depth(3));
      assertEquals(expected.score(), actual.score());
      assertEquals(3, actual.depth());
    }
  }

  @Test
  void handlesTerminalPositionsAndMateDistances() {
    var engine = new KnightshadeEngine(4);
    var mate = engine.search("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", SearchLimits.depth(4));
    assertNull(mate.move());
    assertEquals(-Scores.MATE, mate.score());
    var stalemate = engine.search("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1", SearchLimits.depth(4));
    assertNull(stalemate.move());
    assertEquals(0, stalemate.score());
    var mateInOne = engine.search("6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1", SearchLimits.depth(4));
    assertEquals("e1e8", mateInOne.move().toUci());
    assertEquals(Scores.MATE - 1, mateInOne.score());
  }

  @Test
  void preservesRepetitionHistoryAndInputBoard() {
    Board board = FenParser.parse("7k/8/8/4q3/8/8/8/4R2K w - - 0 1");
    String fen = board.toFen();
    long originalKey = board.zobristKey();
    var capture = new LegalMoveGenerator().generate(board).stream()
        .filter(move -> move.toUci().equals("e1e5")).findFirst().orElseThrow();
    board.make(capture);
    var history = new HashMap<>(Map.of(originalKey, 1, board.zobristKey(), 2));
    board.unmake();
    var originalHistory = new HashMap<>(history);
    var result = new ParallelRootSearch(4).search(board, SearchLimits.depth(4),
        StopSignal.never(), history);
    assertEquals(capture, result.move());
    assertEquals(0, result.score());
    assertEquals(fen, board.toFen());
    assertEquals(originalKey, board.zobristKey());
    assertEquals(originalHistory, history);
  }

  @Test
  void cancelledBeforeStartStillReturnsALegalFallback() {
    var result = new KnightshadeEngine(4).search(START, SearchLimits.depth(20), () -> true);
    assertTrue(new LegalMoveGenerator().generate(FenParser.parse(START)).contains(result.move()));
    assertEquals(0, result.depth());
    assertEquals(0, result.nodes());
  }

  @Test
  void parallelWorkersOverlapAndCancellationReturnsOnlyTheLastCompleteDepth() throws Exception {
    AtomicBoolean cancelled = new AtomicBoolean();
    var probe = new BlockingProbe();
    var search = new ParallelRootSearch(3, probe::evaluator);
    try (var caller = Executors.newSingleThreadExecutor()) {
      var future = caller.submit(() -> search.search(FenParser.parse(START),
          SearchLimits.depth(20), cancelled::get));
      try {
        assertTrue(probe.entered.await(5, TimeUnit.SECONDS), "two workers must run concurrently");
        cancelled.set(true);
      } finally {
        cancelled.set(true);
        probe.release.countDown();
      }
      var result = future.get(5, TimeUnit.SECONDS);
      var expected = new KnightshadeEngine(1).search(START, SearchLimits.depth(2));
      assertEquals(2, result.depth(), "the interrupted third iteration must not be published");
      assertEquals(expected.move(), result.move());
      assertEquals(expected.score(), result.score());
      assertTrue(result.nodes() > 0);
      assertEquals(2, probe.threads.size());
      probe.assertStopped();
    }
  }

  @Test
  void interruptingTheCallerStopsWorkersAndPreservesTheInterruptFlag() throws Exception {
    var probe = new BlockingProbe();
    AtomicBoolean interruptedOnReturn = new AtomicBoolean();
    var search = new ParallelRootSearch(3, probe::evaluator);
    try (var caller = Executors.newSingleThreadExecutor()) {
      var callerThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
      var future = caller.submit(() -> {
        callerThread.set(Thread.currentThread());
        var result = search.search(FenParser.parse(START), SearchLimits.depth(20), StopSignal.never());
        interruptedOnReturn.set(Thread.currentThread().isInterrupted());
        return result;
      });
      try {
        assertTrue(probe.entered.await(5, TimeUnit.SECONDS));
        callerThread.get().interrupt();
      } finally {
        probe.release.countDown();
      }
      assertNotNull(future.get(5, TimeUnit.SECONDS).move());
      assertTrue(interruptedOnReturn.get());
      probe.assertStopped();
    }
  }

  @Test
  void workerFailureIsPropagatedAfterEveryWorkerHasStopped() {
    Set<Thread> workers = ConcurrentHashMap.newKeySet();
    var search = new ParallelRootSearch(4, () -> {
      var delegate = new PositionalEvaluator();
      return position -> {
        if (isWorker()) {
          workers.add(Thread.currentThread());
          throw new IllegalArgumentException("injected evaluator failure");
        }
        return delegate.evaluate(position);
      };
    });
    var failure = assertThrows(IllegalStateException.class, () -> search.search(
        FenParser.parse(START), SearchLimits.depth(6), StopSignal.never()));
    assertEquals("injected evaluator failure", failure.getCause().getMessage());
    assertFalse(workers.isEmpty());
    workers.forEach(thread -> assertFalse(thread.isAlive()));
    assertNotNull(new KnightshadeEngine(2).search(START, SearchLimits.depth(3)).move());
  }

  @Test
  void timedSearchStopsAndTheSameEngineCanServeConcurrentRequests() throws Exception {
    var engine = new KnightshadeEngine(3);
    try (var callers = Executors.newFixedThreadPool(2)) {
      var requests = new ArrayList<java.util.concurrent.Future<SearchResult>>();
      for (int i = 0; i < 4; i++) {
        requests.add(callers.submit(() -> engine.search(START,
            SearchLimits.timeOnly(Duration.ofMillis(100)))));
      }
      var legal = new LegalMoveGenerator().generate(FenParser.parse(START));
      for (var request : requests) {
        var result = request.get(5, TimeUnit.SECONDS);
        assertTrue(legal.contains(result.move()));
        assertTrue(result.elapsedMillis() < 2000, "the time limit must be shared, not per variant");
      }
    }
  }

  @Test
  void rejectsInvalidWorkerCounts() {
    assertThrows(IllegalArgumentException.class, () -> new KnightshadeEngine(0));
    assertThrows(IllegalArgumentException.class, () -> new KnightshadeEngine(33));
  }

  private static boolean isWorker() {
    return Thread.currentThread().getName().startsWith("knightshade-search-");
  }

  private static final class BlockingProbe {
    private final CountDownLatch entered = new CountDownLatch(2);
    private final CountDownLatch release = new CountDownLatch(1);
    private final Set<Thread> threads = ConcurrentHashMap.newKeySet();

    private com.knightshade.engine.evaluation.Evaluator evaluator() {
      var delegate = new PositionalEvaluator();
      var active = new AtomicInteger();
      var visited = new AtomicBoolean();
      return position -> {
        assertEquals(1, active.incrementAndGet(), "an evaluator must never be shared concurrently");
        try {
          if (isWorker() && visited.compareAndSet(false, true)) {
            threads.add(Thread.currentThread());
            entered.countDown();
            try {
              assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              throw new AssertionError(exception);
            }
          }
          return delegate.evaluate(position);
        } finally {
          active.decrementAndGet();
        }
      };
    }

    private void assertStopped() {
      threads.forEach(thread -> assertFalse(thread.isAlive(), "worker must not outlive search"));
    }
  }
}
