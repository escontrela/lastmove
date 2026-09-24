package com.escontrela.lastmove.infrastructure.engine.knightshade;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.EngineAnalysisResult;
import com.escontrela.lastmove.application.computer.EngineScore;
import com.escontrela.lastmove.application.computer.PonderRequest;
import com.escontrela.lastmove.application.computer.PonderSettings;
import com.escontrela.lastmove.application.computer.PonderState;
import com.escontrela.lastmove.application.computer.PonderResourceCoordinator;
import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.service.FenService;
import com.knightshade.engine.KnightshadeEngine;
import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.SearchTelemetryListener;
import com.knightshade.engine.api.SearchTelemetryContext;
import com.knightshade.engine.api.SearchTelemetryEvent;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import com.knightshade.engine.search.PonderSearchContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process {@link ComputerMoveEngine} backed by the embedded Knightshade engine.
 *
 * <p>The adapter keeps the same FEN-in / UCI-move-out shape as the external Sunfish UCI engine, but
 * runs the search in memory on a virtual thread. The position is serialized with {@link FenService}
 * and the resulting engine move is mapped directly onto a domain {@link MoveCommand}.
 */
public final class KnightshadeMoveEngine implements ComputerMoveEngine {

  private static final Logger log = LoggerFactory.getLogger(KnightshadeMoveEngine.class);
  private static final String EVALUATOR_CONFIGURATION_ID = "knightshade-default-v1";
  private static final ExecutorService PONDER_EXECUTOR =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("knightshade-ponder-", 0).factory());
  private static final AtomicReference<PonderSession> ACTIVE_PONDER_TASK = new AtomicReference<>();

  private final KnightshadeEngine engine;
  private final FenService fenService;
  private final ComputerEngineDescriptor descriptor;
  private final KnightshadeTelemetryService telemetryService;
  private final Supplier<PonderSettings> ponderSettings;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("knightshade-", 0).factory());
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicBoolean thinking = new AtomicBoolean();
  private final AtomicReference<AtomicBoolean> activeSearchCancellation = new AtomicReference<>();
  private final AtomicReference<PonderSession> ponderSession = new AtomicReference<>();
  private volatile PonderState lastPonderOutcome = PonderState.IDLE;

  public KnightshadeMoveEngine(
      KnightshadeEngine engine, FenService fenService, ComputerEngineDescriptor descriptor) {
    this(engine, fenService, descriptor, null, PonderSettings.defaults());
  }

  public KnightshadeMoveEngine(
      KnightshadeEngine engine, FenService fenService, ComputerEngineDescriptor descriptor,
      KnightshadeTelemetryService telemetryService) {
    this(engine, fenService, descriptor, telemetryService, PonderSettings.defaults());
  }

  public KnightshadeMoveEngine(
      KnightshadeEngine engine, FenService fenService, ComputerEngineDescriptor descriptor,
      KnightshadeTelemetryService telemetryService, PonderSettings ponderSettings) {
    this(engine, fenService, descriptor, telemetryService, () -> ponderSettings);
  }

  public KnightshadeMoveEngine(
      KnightshadeEngine engine, FenService fenService, ComputerEngineDescriptor descriptor,
      KnightshadeTelemetryService telemetryService, Supplier<PonderSettings> ponderSettings) {
    this.engine = Objects.requireNonNull(engine, "engine must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    this.telemetryService = telemetryService;
    this.ponderSettings = Objects.requireNonNull(ponderSettings, "ponderSettings must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return descriptor;
  }

  @Override
  public CompletionStage<Void> start() {
    if (closed.get()) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public boolean isRunning() {
    return !closed.get();
  }

  @Override
  public boolean isThinking() {
    return thinking.get();
  }

  @Override
  public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
    ComputerMoveRequest required =
        Objects.requireNonNull(request, "request must not be null");
    if (closed.get()) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    PonderResourceCoordinator.RealSearchLease resourceLease =
        PonderResourceCoordinator.beginRealSearch(required.gameId(), required.generation());
    AtomicBoolean requestCancellation = beginSearchRequest();
    thinking.set(true);
    try {
      return CompletableFuture.supplyAsync(() -> chooseMoveBlocking(required, requestCancellation), executor)
          .whenComplete((ignored, failure) -> resourceLease.close());
    } catch (RejectedExecutionException exception) {
      resourceLease.close();
      finishSearchRequest(requestCancellation);
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public CompletionStage<EngineAnalysisResult> analyze(ComputerMoveRequest request) {
    ComputerMoveRequest required =
        Objects.requireNonNull(request, "request must not be null");
    if (closed.get()) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    PonderResourceCoordinator.RealSearchLease resourceLease =
        PonderResourceCoordinator.beginAnalysis();
    AtomicBoolean requestCancellation = beginSearchRequest();
    thinking.set(true);
    try {
      return CompletableFuture.supplyAsync(() -> analyzeBlocking(required, false, requestCancellation), executor)
          .whenComplete((ignored, failure) -> resourceLease.close());
    } catch (RejectedExecutionException exception) {
      resourceLease.close();
      finishSearchRequest(requestCancellation);
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public void cancelSearch() {
    AtomicBoolean active = activeSearchCancellation.get();
    if (active != null) active.set(true);
  }

  /**
   * Starts a bounded preparation on the single global speculative worker, if it is currently free.
   */
  @Override
  public void startPonder(PonderRequest request) {
    PonderRequest required = Objects.requireNonNull(request, "request must not be null");
    cancelPonder();
    PonderSettings currentSettings = ponderSettings.get();
    if (closed.get() || thinking.get() || !currentSettings.enabled()
        || !currentSettings.speculativeWorkerEnabled() || !required.settings().equals(currentSettings)
        || !EVALUATOR_CONFIGURATION_ID.equals(required.evaluatorConfigurationId())) {
      return;
    }
    PonderSession session = new PonderSession(this, required);
    session.lease = PonderResourceCoordinator.tryStartPonder(required.gameId(), required.generation(),
        required.shareWithSameGameRealSearch(), () -> cancelPonderSession(session));
    if (session.lease == null) return;
    if (!ponderSession.compareAndSet(null, session)) {
      session.lease.close();
      return;
    }
    ACTIVE_PONDER_TASK.set(session);
    try {
      PONDER_EXECUTOR.submit(() -> preparePonder(session));
    } catch (RejectedExecutionException exception) {
      ACTIVE_PONDER_TASK.compareAndSet(session, null);
      ponderSession.compareAndSet(session, null);
      session.lease.close();
      session.finished.complete(null);
    }
  }

  /** Invalidates the current speculative token without touching a real move search. */
  @Override
  public void cancelPonder() {
    PonderSession active = ponderSession.getAndSet(null);
    if (active != null) cancelPonderSession(active);
  }

  PonderState ponderState() {
    PonderSession active = ponderSession.get();
    return active == null ? PonderState.IDLE : active.state.get();
  }

  PonderSearchContext preparedPonderContext() {
    PonderSession active = ponderSession.get();
    return active == null ? null : active.context;
  }

  PonderState lastPonderOutcome() {
    return lastPonderOutcome;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      cancelPonder();
      AtomicBoolean active = activeSearchCancellation.get();
      if (active != null) active.set(true);
      executor.shutdownNow();
    }
  }

  private static void cancelPonderSession(PonderSession session) {
    session.discarded.set(true);
    session.cancelled.set(true);
    session.state.set(PonderState.CANCELLED);
    PonderSearchContext context = session.context;
    if (context != null) context.cancel();
    if (session.finished.isDone()) session.lease.close();
  }

  private AtomicBoolean beginSearchRequest() {
    AtomicBoolean token = new AtomicBoolean();
    AtomicBoolean previous = activeSearchCancellation.getAndSet(token);
    if (previous != null) previous.set(true);
    return token;
  }

  private void finishSearchRequest(AtomicBoolean token) {
    if (activeSearchCancellation.compareAndSet(token, null)) thinking.set(false);
  }

  private MoveCommand chooseMoveBlocking(ComputerMoveRequest request, AtomicBoolean requestCancellation) {
    EngineAnalysisResult result = analyzeBlocking(request, true, requestCancellation);
    return result
        .bestMove()
        .orElseThrow(
            () -> new ComputerEngineException("Knightshade found no playable move"));
  }

  /**
   * Only a move selected for an active game emits Blood Pressure data. Generic analysis drives
   * controls such as the strength bar and must remain invisible to match telemetry.
   */
  private EngineAnalysisResult analyzeBlocking(ComputerMoveRequest request, boolean gameMove,
      AtomicBoolean requestCancellation) {
    long startedAt = System.nanoTime();
    try {
      String fen = fenService.fromSnapshot(request.position()).getValue();
      List<String> positionHistory =
          request.positionHistory().stream()
              .map(position -> fenService.fromSnapshot(position).getValue())
              .toList();
      long maxTimeMillis = request.maximumThinkingTime().toMillis();
      log.debug("Knightshade search started: maxTimeMs={}", maxTimeMillis);
      SearchTelemetryListener listener = gameMove && telemetryService != null && telemetryService.isEnabled()
          ? telemetryService::publish : SearchTelemetryListener.NONE;
      SearchTelemetryContext telemetryContext = listener == SearchTelemetryListener.NONE ? null
          : SearchTelemetryContext.forSearch(request.gameId() == null
                  ? telemetryService.gameId() : request.gameId().value().toString(), fen,
              SearchLimits.timeOnly(request.maximumThinkingTime()), positionHistory);
      SearchResult result = gameMove
          ? resumeOrSearch(request, fen, positionHistory, telemetryContext, listener, requestCancellation)
          : engine.search(fen, positionHistory,
              SearchLimits.timeOnly(request.maximumThinkingTime()), requestCancellation::get, listener,
              telemetryContext);
      EngineScore score =
          result.mate()
              ? EngineScore.mateIn(signedMatePlies(result))
              : EngineScore.centipawns(result.score());
      log.debug(
          "Knightshade analysed {} score={} depth={} nodes={} elapsedMs={} totalMs={}",
          result.move() == null ? "(none)" : result.move().toUci(),
          result.score(),
          result.depth(),
          result.nodes(),
          result.elapsedMillis(),
          (System.nanoTime() - startedAt) / 1_000_000L);
      if (result.move() == null) {
        return new EngineAnalysisResult(
            Optional.empty(), Optional.of(score), Optional.of(result.depth()), Optional.of(result.nodes()));
      }
      MoveCommand move =
          new MoveCommand(
              result.move().from(),
              result.move().to(),
              Optional.ofNullable(result.move().promotion()));
      return new EngineAnalysisResult(
          Optional.of(move), Optional.of(score), Optional.of(result.depth()), Optional.of(result.nodes()));
    } catch (ComputerEngineException exception) {
      log.error("Knightshade search failed", exception);
      throw exception;
    } finally {
      finishSearchRequest(requestCancellation);
    }
  }

  private SearchResult resumeOrSearch(ComputerMoveRequest request, String fen,
      List<String> positionHistory, SearchTelemetryContext telemetryContext,
      SearchTelemetryListener listener, AtomicBoolean requestCancellation) {
    PonderSession session = ponderSession.getAndSet(null);
    stopOtherGlobalPonder(session, request);
    SearchResult reusedResult = null;
    if (session != null) {
      PonderSearchContext prepared = null;
      boolean transferred = false;
      try {
        session.cancelled.set(true); // Stop speculation at the next safe search boundary.
        prepared = awaitPonderTask(session);
        PonderSettings currentSettings = ponderSettings.get();
        boolean sameGame = request.gameId() != null
            && request.gameId().equals(session.request.gameId())
            && request.generation() == session.request.generation();
        boolean eligibleDecision = prepared != null && sameGame
            && currentSettings.equals(session.request.settings());
        boolean predictionMatched = eligibleDecision && prepared.predictionMatches(fen, positionHistory);
        if (eligibleDecision && listener != SearchTelemetryListener.NONE && telemetryContext != null) {
          try {
            listener.onSnapshot(SearchTelemetrySnapshot.ponderDecision(telemetryContext,
                predictionMatched ? SearchTelemetryEvent.PONDER_HIT : SearchTelemetryEvent.PONDER_MISS,
                predictionMatched && prepared.ready() ? prepared.completedContinuationDepth() : 0));
          } catch (RuntimeException telemetryFailure) {
            log.debug("Unable to publish Knightshade ponder decision: {}", telemetryFailure.getMessage());
          }
        }
        if (predictionMatched && prepared.ready()) {
          try {
            reusedResult = prepared.resume(fen, positionHistory,
                SearchLimits.timeOnly(request.maximumThinkingTime()), requestCancellation::get,
                listener, 1, telemetryContext).orElse(null);
            transferred = reusedResult != null;
          } catch (RuntimeException resumeFailure) {
            log.debug("Knightshade ponder continuation failed; starting a fresh search: {}",
                resumeFailure.getMessage());
          }
        }
        session.state.set(eligibleDecision
            ? (predictionMatched ? PonderState.HIT : PonderState.MISS) : PonderState.CANCELLED);
        lastPonderOutcome = session.state.get();
      } finally {
        if (prepared != null && !transferred) prepared.cancel();
        session.lease.close();
        session.state.set(PonderState.IDLE);
      }
    }
    if (reusedResult != null) return reusedResult;
    return engine.search(fen, positionHistory, SearchLimits.timeOnly(request.maximumThinkingTime()),
        requestCancellation::get, listener, telemetryContext);
  }

  private void preparePonder(PonderSession session) {
    try {
      if (session.cancelled.get()) return;
      String rootFen = fenService.fromSnapshot(session.request.positionAfterOurMove()).getValue();
      List<String> history = session.request.positionHistory().stream()
          .map(position -> fenService.fromSnapshot(position).getValue()).toList();
      PonderSettings settings = session.request.settings();
      session.state.set(PonderState.PREDICTING);
      if (telemetryService != null && telemetryService.isEnabled() && !session.cancelled.get()) {
        var context = SearchTelemetryContext.forSearch(session.request.gameId().value().toString(),
            rootFen, SearchLimits.bounded(settings.predictionBudget(), settings.predictionDepth()), history);
        telemetryService.publish(SearchTelemetrySnapshot.ponderStarted(context));
      }
      var prepared = engine.preparePonder(rootFen, history,
          SearchLimits.bounded(settings.predictionBudget(), settings.predictionDepth()),
          SearchLimits.bounded(settings.continuationBudget(), settings.continuationDepth()),
          session.cancelled::get, () -> session.state.set(PonderState.PONDERING));
      prepared.ifPresentOrElse(context -> {
        if (ponderSettings.get().equals(session.request.settings()) && !session.discarded.get()) {
          session.context = context;
          session.state.set(PonderState.READY);
        } else {
          context.cancel();
          session.state.set(PonderState.CANCELLED);
        }
      }, () -> session.state.set(PonderState.IDLE));
    } catch (RuntimeException exception) {
      log.debug("Knightshade ponder preparation was discarded: {}", exception.getMessage());
    } finally {
      ACTIVE_PONDER_TASK.compareAndSet(session, null);
      if (session.state.get() != PonderState.READY || session.discarded.get()) {
        PonderSearchContext context = session.context;
        if (context != null) context.cancel();
      }
      // The permit limits running workers; a completed private context consumes no CPU.
      session.lease.close();
      session.finished.complete(null);
    }
  }

  private static void stopOtherGlobalPonder(PonderSession current, ComputerMoveRequest request) {
    PonderSession active = ACTIVE_PONDER_TASK.get();
    if (active == null || active == current) return;
    if (!active.discarded.get() && active.request.shareWithSameGameRealSearch()
        && active.request.gameId().equals(request.gameId())) return;
    active.discarded.set(true);
    active.cancelled.set(true);
    active.state.set(PonderState.CANCELLED);
    awaitPonderTask(active);
    PonderSearchContext context = active.context;
    if (context != null) context.cancel();
    active.owner.ponderSession.compareAndSet(active, null);
    active.state.set(PonderState.IDLE);
  }

  private static PonderSearchContext awaitPonderTask(PonderSession session) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          session.finished.get(); // Completion is the ownership-transfer barrier for mutable state.
          break;
        } catch (InterruptedException exception) {
          interrupted = true;
          session.cancelled.set(true);
        } catch (ExecutionException exception) {
          Throwable cause = exception.getCause();
          log.debug("Knightshade ponder worker failed: {}",
              cause == null ? "unknown failure" : cause.getMessage());
          break;
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
    return session.context;
  }

  private static int signedMatePlies(SearchResult result) {
    int plies = result.matePlies();
    return result.score() > 0 ? plies : -plies;
  }

  private ComputerEngineException closedEngineException() {
    return new ComputerEngineException("The Knightshade engine has already been closed");
  }

  private static final class PonderSession {
    private final KnightshadeMoveEngine owner;
    private final PonderRequest request;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean discarded = new AtomicBoolean();
    private final AtomicReference<PonderState> state =
        new AtomicReference<>(PonderState.PREDICTING);
    private final CompletableFuture<Void> finished = new CompletableFuture<>();
    private volatile PonderSearchContext context;
    private PonderResourceCoordinator.PonderLease lease;

    private PonderSession(KnightshadeMoveEngine owner, PonderRequest request) {
      this.owner = owner;
      this.request = request;
    }
  }
}
