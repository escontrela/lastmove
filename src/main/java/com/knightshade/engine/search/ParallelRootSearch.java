package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.api.SearchTelemetryListener;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.evaluation.PositionalEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.ordering.HistoryTable;
import com.knightshade.engine.ordering.KillerMoves;
import com.knightshade.engine.ordering.MvvLvaMoveOrderer;
import com.knightshade.engine.ordering.OrderingContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Root PVS splitting: search the first move before releasing the remaining alternatives.
 * Only root bounds and cancellation are shared. Every worker owns its board, repetition map,
 * evaluator, transposition table, killers and history. All state is scoped to one invocation.
 */
public final class ParallelRootSearch implements Search {
  private static final int MAX_THREADS = 32;
  private static final AtomicInteger POOL_IDS = new AtomicInteger();
  private final int threads;
  private final Supplier<? extends Evaluator> evaluators;
  private final ThreadLocal<SearchTelemetryListener> telemetryListeners =
      ThreadLocal.withInitial(() -> SearchTelemetryListener.NONE);
  private final ThreadLocal<SearchTelemetryContext> telemetryContexts = new ThreadLocal<>();

  public ParallelRootSearch(int threads) {
    this(threads, PositionalEvaluator::new);
  }

  public int threads() { return threads; }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop,
      Map<Long, Integer> positionOccurrences, SearchTelemetryListener listener,
      int requestedWorkers, int effectiveWorkers) {
    return search(board, limits, stop, positionOccurrences, listener, requestedWorkers,
        effectiveWorkers, null);
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop,
      Map<Long, Integer> positionOccurrences, SearchTelemetryListener listener,
      int requestedWorkers, int effectiveWorkers, SearchTelemetryContext context) {
    telemetryListeners.set(listener == null ? SearchTelemetryListener.NONE : listener);
    telemetryContexts.set(context);
    try {
      return search(board, limits, stop, positionOccurrences);
    } finally {
      telemetryListeners.remove();
      telemetryContexts.remove();
    }
  }

  /** The supplier must return a fresh evaluator for each worker. */
  ParallelRootSearch(int threads, Supplier<? extends Evaluator> evaluators) {
    if (threads < 1 || threads > MAX_THREADS) {
      throw new IllegalArgumentException("threads must be between 1 and " + MAX_THREADS);
    }
    this.threads = threads;
    this.evaluators = Objects.requireNonNull(evaluators);
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop) {
    return search(board, limits, stop, Map.of());
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop,
      Map<Long, Integer> positionOccurrences) {
    Objects.requireNonNull(board);
    Objects.requireNonNull(limits);
    Objects.requireNonNull(stop);
    Objects.requireNonNull(positionOccurrences);
    long started = System.nanoTime();
    AtomicBoolean cancelled = new AtomicBoolean();
    StopSignal requestStop = () -> {
      if (cancelled.get()) {
        return true;
      }
      if (Thread.currentThread().isInterrupted() || stop.shouldStop()
          || limits.maxTimeMillis() > 0 && elapsedMillis(started) >= limits.maxTimeMillis()) {
        cancelled.set(true);
        return true;
      }
      return false;
    };
    SearchTelemetryListener telemetryListener = telemetryListeners.get();
    SearchTelemetryContext telemetryContext = telemetryContexts.get();
    boolean telemetryEnabled = telemetryListener != SearchTelemetryListener.NONE;
    if (threads == 1) {
      IterativeDeepeningSearch sequential = new IterativeDeepeningSearch(new LegalMoveGenerator(), evaluators.get());
      return telemetryEnabled
          ? sequential.search(board.copy(), limits, stop, positionOccurrences, telemetryListener,
              threads, 1, telemetryContext)
          : sequential.search(board.copy(), limits, stop, positionOccurrences);
    }

    Board root = board.copy();
    List<Move> legal = new LegalMoveGenerator().generate(root);
    if (legal.isEmpty()) {
      SearchResult result = new SearchResult(null, root.inCheck(root.sideToMove()) ? -Scores.MATE : 0,
          0, 0, elapsedMillis(started));
      if (telemetryEnabled) emit(new SearchStats(), result, started, threads, 0,
          SearchTelemetryEvent.SEARCH_FINISHED, terminalReason(limits, started, stop, result));
      return result;
    }
    Move bestMove = legal.getFirst();
    int bestScore = 0;
    int completedDepth = 0;
    if (requestStop.shouldStop()) {
      SearchResult result = new SearchResult(bestMove, bestScore, 0, 0, elapsedMillis(started));
      if (telemetryEnabled) emit(new SearchStats(), result, started, threads, 0,
          SearchTelemetryEvent.SEARCH_FINISHED, terminalReason(limits, started, stop, result));
      return result;
    }

    int workerCount = Math.min(threads, legal.size());
    List<Worker> workers = new ArrayList<>();
    workers.add(new Worker(root, positionOccurrences, evaluators.get(), telemetryEnabled, threads, workerCount));
    ExecutorService executor = workerCount > 1
        ? Executors.newFixedThreadPool(workerCount - 1,
            Thread.ofPlatform().daemon(true)
                .name("knightshade-search-" + POOL_IDS.incrementAndGet() + "-", 0).factory())
        : null;
    try {
      int maxDepth = limits.maxDepth() > 0 ? limits.maxDepth() : 64;
      for (int depth = 1; depth <= maxDepth && !requestStop.shouldStop(); depth++) {
        // Small iterations stay on the caller to avoid dispatch overhead. Workers retain their
        // own caches between subsequent depths and aspiration attempts in this invocation.
        if (depth == 3) {
          while (workers.size() < workerCount && !requestStop.shouldStop()) {
            workers.add(new Worker(root, positionOccurrences, evaluators.get(), telemetryEnabled, threads, workerCount));
          }
        }
        int delta = 25;
        int alpha = depth == 1 ? -Scores.INF : bestScore - delta;
        int beta = depth == 1 ? Scores.INF : bestScore + delta;
        Move orderingMove = bestMove;
        while (!requestStop.shouldStop()) {
          Worker primary = workers.getFirst();
          List<Move> ordered = new MvvLvaMoveOrderer().order(root, legal,
              new OrderingContext(0, primary.killers, primary.history, orderingMove));
          RootResult result = searchIteration(workers, executor, ordered, depth,
              alpha, beta, requestStop, cancelled);
          if (requestStop.shouldStop()) {
            break;
          }
          orderingMove = result.move();
          if (result.score() <= alpha) {
            if (Scores.isMate(result.score()) && (alpha != -Scores.INF || beta != Scores.INF)) {
              if (telemetryEnabled) workers.getFirst().search.telemetryStats().mateConfirmations++;
              alpha = -Scores.INF;
              beta = Scores.INF;
              delta = Scores.INF;
              continue;
            }
            if (telemetryEnabled) workers.getFirst().search.telemetryStats().aspirationRetries++;
            alpha = Math.max(-Scores.INF, alpha - delta);
          } else if (result.score() >= beta) {
            if (Scores.isMate(result.score()) && (alpha != -Scores.INF || beta != Scores.INF)) {
              if (telemetryEnabled) workers.getFirst().search.telemetryStats().mateConfirmations++;
              alpha = -Scores.INF;
              beta = Scores.INF;
              delta = Scores.INF;
              continue;
            }
            if (telemetryEnabled) workers.getFirst().search.telemetryStats().aspirationRetries++;
            beta = Math.min(Scores.INF, beta + delta);
          } else {
            bestMove = result.move();
            bestScore = result.score();
            completedDepth = depth;
            if (telemetryEnabled) emit(aggregateStats(workers), bestMove, bestScore, depth,
                started, threads, workers.size(), SearchTelemetryEvent.ITERATION_COMPLETED,
                StopReason.COMPLETED);
            break;
          }
          delta *= 2;
        }
        if (completedDepth == depth && Scores.isMate(bestScore)) {
          break;
        }
      }
    } finally {
      cancelled.set(true);
      if (executor != null) {
        shutdownAndAwait(executor);
      }
    }
    long nodes = workers.stream().mapToLong(worker -> worker.search.nodesVisited()).sum();
    SearchResult result = new SearchResult(bestMove, bestScore, completedDepth, nodes, elapsedMillis(started));
    if (telemetryEnabled) emit(aggregateStats(workers), result, started, threads, workers.size(),
        SearchTelemetryEvent.SEARCH_FINISHED, terminalReason(limits, started, stop, result));
    return result;
  }

  private SearchStats aggregateStats(List<Worker> workers) {
    SearchStats total = new SearchStats();
    for (Worker worker : workers) total.merge(worker.search.telemetryStats());
    return total;
  }

  private void emit(SearchStats stats, Move move, int score, int depth, long started,
      int requested, int active, SearchTelemetryEvent event, StopReason reason) {
    if (stats == null) return;
    try {
      telemetryListeners.get().onSnapshot(stats.snapshot(telemetryContexts.get(), event, depth, move, score, requested, active,
          reason, elapsedMillis(started)));
    } catch (RuntimeException ignored) { }
  }

  private void emit(SearchStats stats, SearchResult result, long started,
      int requested, int active, SearchTelemetryEvent event, StopReason reason) {
    emit(stats, result.move(), result.score(), result.depth(), started, requested, active, event, reason);
  }

  private StopReason terminalReason(SearchLimits limits, long started, StopSignal externalStop, SearchResult result) {
    if (result.move() == null) return Scores.isMate(result.score()) ? StopReason.MATE : StopReason.NO_LEGAL_MOVE;
    if (externalStop.shouldStop()) return StopReason.CANCELLED;
    if (Scores.isMate(result.score())) return StopReason.MATE;
    if (limits.maxDepth() > 0 && result.depth() >= limits.maxDepth()) return StopReason.DEPTH_LIMIT;
    return limits.maxTimeMillis() > 0 ? StopReason.TIME_LIMIT : StopReason.COMPLETED;
  }

  private RootResult searchIteration(List<Worker> workers, ExecutorService executor,
      List<Move> moves, int depth, int alpha, int beta, StopSignal requestStop,
      AtomicBoolean cancelled) {
    Worker primary = workers.getFirst();
    int firstScore = primary.search(moves.getFirst(), depth, alpha, beta, false, requestStop);
    RootIteration iteration = new RootIteration(moves.getFirst(), firstScore, alpha, beta);
    if (requestStop.shouldStop() || firstScore >= beta || moves.size() == 1) {
      return iteration.result();
    }
    StopSignal iterationStop = () -> iteration.cutoff.get() || requestStop.shouldStop();
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int i = 1; i < workers.size(); i++) {
        Worker worker = workers.get(i);
        futures.add(executor.submit(() -> {
          try {
            searchRemaining(worker, moves, depth, iteration, iterationStop);
          } catch (RuntimeException | Error failure) {
            cancelled.set(true);
            throw failure;
          }
        }));
      }
      searchRemaining(primary, moves, depth, iteration, iterationStop);
    } catch (RuntimeException | Error failure) {
      cancelled.set(true);
      throw failure;
    } finally {
      awaitAll(futures, cancelled);
    }
    return iteration.result();
  }

  private void searchRemaining(Worker worker, List<Move> moves, int depth,
      RootIteration iteration, StopSignal stop) {
    while (!stop.shouldStop()) {
      int index = iteration.next.getAndIncrement();
      if (index >= moves.size()) {
        return;
      }
      Move move = moves.get(index);
      int alpha = iteration.alpha.get();
      // A stale alpha only does extra work: an improving scout is confirmed with a full window
      // before publication. Fail-low bounds cannot replace a better, confirmed root result.
      int score = worker.search(move, depth, alpha, iteration.beta, true, stop);
      if (!stop.shouldStop()) {
        iteration.accept(move, score);
      }
    }
  }

  private void awaitAll(List<Future<?>> futures, AtomicBoolean cancelled) {
    boolean interrupted = false;
    IllegalStateException failure = null;
    for (Future<?> future : futures) {
      boolean done = false;
      while (!done) {
        try {
          future.get();
          done = true;
        } catch (InterruptedException exception) {
          interrupted = true;
          cancelled.set(true);
        } catch (ExecutionException exception) {
          cancelled.set(true);
          done = true;
          if (failure == null) {
            failure = new IllegalStateException("Knightshade search worker failed", exception.getCause());
          } else {
            failure.addSuppressed(exception.getCause());
          }
        }
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    if (failure != null) {
      throw failure;
    }
  }

  /** Stops all root workers before returning, even when the caller was interrupted. */
  private void shutdownAndAwait(ExecutorService executor) {
    boolean interrupted = Thread.interrupted();
    executor.shutdownNow();
    while (!executor.isTerminated()) {
      try {
        executor.awaitTermination(1, TimeUnit.DAYS);
      } catch (InterruptedException exception) {
        interrupted = true;
        executor.shutdownNow();
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static long elapsedMillis(long started) {
    return (System.nanoTime() - started) / 1_000_000L;
  }

  private record RootResult(Move move, int score) {}

  private static final class RootIteration {
    private final AtomicInteger next = new AtomicInteger(1);
    private final AtomicInteger alpha;
    private final AtomicBoolean cutoff = new AtomicBoolean();
    private final int beta;
    private Move bestMove;
    private int bestScore;

    private RootIteration(Move move, int score, int alpha, int beta) {
      this.bestMove = move;
      this.bestScore = score;
      this.alpha = new AtomicInteger(Math.max(alpha, score));
      this.beta = beta;
    }

    private synchronized void accept(Move move, int score) {
      if (score > bestScore) {
        bestMove = move;
        bestScore = score;
      }
      alpha.accumulateAndGet(score, Math::max);
      if (score >= beta) {
        cutoff.set(true);
      }
    }

    private synchronized RootResult result() {
      return new RootResult(bestMove, bestScore);
    }
  }

  private static final class Worker {
    private final Board board;
    private final Map<Long, Integer> repetitions;
    private final KillerMoves killers = new KillerMoves();
    private final HistoryTable history = new HistoryTable();
    private final IterativeDeepeningSearch search;

    private Worker(Board root, Map<Long, Integer> occurrences, Evaluator evaluator,
        boolean telemetryEnabled, int requestedWorkers, int effectiveWorkers) {
      board = root.copy();
      repetitions = new HashMap<>(occurrences);
      repetitions.putIfAbsent(board.zobristKey(), 1);
      search = new IterativeDeepeningSearch(new LegalMoveGenerator(), evaluator);
      if (telemetryEnabled) search.enableTelemetry(requestedWorkers, effectiveWorkers);
    }

    private int search(Move move, int depth, int alpha, int beta, boolean scout, StopSignal stop) {
      return search.searchRootMove(board, move, depth, alpha, beta, scout,
          killers, history, repetitions, stop);
    }
  }
}
