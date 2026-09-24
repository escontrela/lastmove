package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.*;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.*;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

/** Runs two engines against each other in process memory only. */
@Service
public final class ComputerVsComputerGameService {
  private final ChessGameFactory games;
  private final Map<String, ComputerMoveEngineProvider> providers;
  private final Clock clock;
  private final KnightshadeTelemetryService telemetry;
  private final ComputerEngineSettingsService engineSettings;
  private final int availableProcessors;
  private final Map<GameId, Runtime> runtimes = new ConcurrentHashMap<>();
  private final ScheduledExecutorService moveScheduler =
      Executors.newSingleThreadScheduledExecutor(
          task -> {
            Thread thread = new Thread(task, "lastmove-engine-match");
            thread.setDaemon(true);
            return thread;
          });

  public ComputerVsComputerGameService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers, Clock clock) {
    this(games, providers, clock, null);
  }
  public ComputerVsComputerGameService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers, Clock clock, KnightshadeTelemetryService telemetry) {
    this(games, providers, clock, telemetry, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public ComputerVsComputerGameService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers,
      Clock clock, KnightshadeTelemetryService telemetry, ComputerEngineSettingsService engineSettings) {
    this(games, providers, clock, telemetry, engineSettings,
        java.lang.Runtime.getRuntime().availableProcessors());
  }
  ComputerVsComputerGameService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers,
      Clock clock, KnightshadeTelemetryService telemetry, ComputerEngineSettingsService engineSettings,
      int availableProcessors) {
    this.games = Objects.requireNonNull(games); this.clock = Objects.requireNonNull(clock); this.telemetry = telemetry;
    this.engineSettings = engineSettings;
    this.availableProcessors = availableProcessors;
    this.providers = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(p -> p.descriptor().id(), p -> p));
  }
  public List<ComputerEngineDescriptor> availableEngines() { return providers.values().stream().map(ComputerMoveEngineProvider::descriptor).sorted(Comparator.comparing(ComputerEngineDescriptor::displayName)).toList(); }
  public List<ComputerVsComputerGameState> gamesInMemory() { return runtimes.keySet().stream().map(this::state).toList(); }
  public CompletionStage<ComputerVsComputerGameState> createGame(ComputerVsComputerConfiguration configuration) {
    ComputerMoveEngineProvider whiteProvider = provider(configuration.whiteEngineId());
    ComputerMoveEngineProvider blackProvider = provider(configuration.blackEngineId());
    GamePlayer whitePlayer =
        new GamePlayer(whiteProvider.descriptor().displayName(), PieceColor.WHITE);
    GamePlayer blackPlayer =
        new GamePlayer(blackProvider.descriptor().displayName(), PieceColor.BLACK);
    ChessGame game =
        configuration
            .startingPosition()
            .map(fen -> games.createFrom(fen, whitePlayer, blackPlayer, Optional.of(configuration.timeControl())))
            .orElseGet(
                () -> games.createInitial(whitePlayer, blackPlayer, Optional.of(configuration.timeControl())));
    LocalGameAdjudication.drawBareKings(game);
    if (telemetry != null && telemetry.isEnabled()
        && (ComputerEngineIds.KNIGHTSHADE.equals(configuration.whiteEngineId())
            || ComputerEngineIds.KNIGHTSHADE.equals(configuration.blackEngineId()))) {
      telemetry.beginSession(game.id().value().toString());
    }
    Runtime runtime = new Runtime(game, configuration, whiteProvider.descriptor(), blackProvider.descriptor(), whiteProvider.create(), blackProvider.create());
    runtimes.put(game.id(), runtime);
    return runtime.white.start().thenCompose(ignored -> runtime.black.start()).thenApply(ignored -> {
      ComputerVsComputerGameState initial;
      synchronized (runtime) {
        runtime.turnStartedAt = game.result().isPresent() ? null : clock.instant();
        runtime.phase = game.result().isPresent()
            ? ComputerGamePhase.FINISHED : ComputerGamePhase.ENGINE_THINKING;
        initial = snapshot(runtime);
      }
      if (game.result().isEmpty()) requestMove(runtime);
      return initial;
    });
  }
  public ComputerVsComputerGameState state(GameId id) {
    Runtime runtime = runtime(id);
    ComputerVsComputerGameState state;
    boolean finishedNow;
    boolean expired;
    synchronized (runtime) {
      finishedNow = LocalGameAdjudication.drawBareKings(runtime.game);
      if (finishedNow) {
        runtime.searchVersion++;
        runtime.phase = ComputerGamePhase.FINISHED;
        runtime.turnStartedAt = null;
      }
      expired = expire(runtime);
      state = snapshot(runtime);
    }
    if (finishedNow || expired) cancelSearches(runtime);
    return state;
  }
  public GameRecord gameRecord(GameId id) { return runtime(id).game.toRecord(); }
  /** Stops the match without assigning either player a result. */
  public ComputerVsComputerGameState stop(GameId id) { Runtime runtime = runtime(id); ComputerVsComputerGameState state; synchronized (runtime) { runtime.searchVersion++; runtime.phase = ComputerGamePhase.FINISHED; runtime.stopped = true; runtime.turnStartedAt = null; runtime.message = Optional.of("Game stopped"); state = snapshot(runtime); } cancelSearches(runtime); return state; }
  public CompletionStage<ComputerVsComputerGameState> restartGame(GameId id) { Runtime old = runtime(id); ComputerVsComputerConfiguration config = old.configuration; closeGame(id); return createGame(config); }
  public boolean closeGame(GameId id) { Runtime runtime = runtimes.remove(id); if (runtime == null) return false; synchronized (runtime) { runtime.searchVersion++; } cancelSearches(runtime); runtime.white.close(); runtime.black.close(); return true; }
  @PreDestroy void closeAll() { moveScheduler.shutdownNow(); new ArrayList<>(runtimes.keySet()).forEach(this::closeGame); }
  /** Starts one engine turn and schedules the next turn after its result.
   *
   * <p>This deliberately does not compose the complete match into one future: the caller must get
   * the initial snapshot immediately so JavaFX can reveal the live board between engine moves.
   */
  private void requestMove(Runtime runtime) {
    final PositionSnapshot position; final long version; final Duration limit;
    final ComputerMoveEngine engine; final List<PositionSnapshot> history;
    final long generation; final PieceColor side;
    boolean expired;
    synchronized (runtime) {
      expired = expire(runtime);
      if (runtime.game.result().isPresent() || runtime.stopped) {
        position = null; version = runtime.searchVersion; engine = null; limit = null;
        history = null; generation = 0; side = null;
      } else {
        position = runtime.game.currentPosition();
        version = ++runtime.searchVersion;
        side = runtime.game.currentTurn();
        engine = side == PieceColor.WHITE ? runtime.white : runtime.black;
        generation = side == PieceColor.WHITE ? runtime.whitePonderGeneration : runtime.blackPonderGeneration;
        history = runtime.game.positionHistory();
        limit = permitted(runtime);
      }
    }
    if (expired) cancelSearches(runtime);
    if (position == null) return;
    PonderResourceCoordinator.RealSearchLease resourceLease =
        PonderResourceCoordinator.beginRealSearch(runtime.game.id(), generation);
    CompletionStage<com.escontrela.lastmove.domain.game.MoveCommand> moveStage;
    try {
      moveStage = engine.chooseMove(new ComputerMoveRequest(position, limit, history,
          runtime.game.id(), generation));
    } catch (RuntimeException failure) {
      resourceLease.close();
      throw failure;
    }
    moveStage.handle((move, failure) -> {
      resourceLease.close();
      boolean continueMatch;
      long nextGeneration = 0;
      PositionSnapshot afterMove = null;
      List<PositionSnapshot> afterHistory = null;
      synchronized (runtime) {
        if (version != runtime.searchVersion) return null;
        if (failure != null) { runtime.phase = ComputerGamePhase.ENGINE_ERROR; runtime.turnStartedAt = null; runtime.message = Optional.of("Computer engine error: " + detail(failure)); return null; }
        expire(runtime); if (runtime.game.result().isPresent() || runtime.stopped || !runtime.game.currentPosition().equals(position)) return null;
        var applied = runtime.game.move(move, elapsed(runtime));
        if (!applied.accepted()) { runtime.phase = ComputerGamePhase.ENGINE_ERROR; runtime.message = Optional.of("The engine returned an illegal move: " + move); return null; }
        LocalGameAdjudication.drawBareKings(runtime.game);
        runtime.turnStartedAt = runtime.game.result().isPresent() ? null : clock.instant();
        runtime.phase = runtime.game.result().isPresent()
            ? ComputerGamePhase.FINISHED : ComputerGamePhase.ENGINE_THINKING;
        continueMatch = runtime.phase == ComputerGamePhase.ENGINE_THINKING;
        if (continueMatch) {
          nextGeneration = side == PieceColor.WHITE
              ? ++runtime.whitePonderGeneration : ++runtime.blackPonderGeneration;
          afterMove = runtime.game.currentPosition();
          afterHistory = runtime.game.positionHistory();
        }
      }
      if (continueMatch) {
        PonderRequest ponderRequest = ponderRequest(runtime, engine, side, nextGeneration,
            afterMove, afterHistory);
        if (ponderRequest != null) {
          boolean stillCurrent;
          synchronized (runtime) {
            stillCurrent = runtime.searchVersion == version && !runtime.stopped
                && runtime.game.result().isEmpty();
          }
          if (stillCurrent) {
            try {
              engine.startPonder(ponderRequest);
            } catch (RuntimeException ignored) {
              // Optional speculation must not interrupt the confirmed game move.
            }
            synchronized (runtime) {
              stillCurrent = runtime.searchVersion == version && !runtime.stopped
                  && runtime.game.result().isEmpty();
            }
            if (!stillCurrent) engine.cancelPonder();
          }
        }
        long delayMillis = runtime.configuration.moveDelay().toMillis();
        moveScheduler.schedule(() -> requestMove(runtime), delayMillis, TimeUnit.MILLISECONDS);
      }
      return null;
    });
  }
  private PonderRequest ponderRequest(Runtime runtime, ComputerMoveEngine engine,
      PieceColor side, long generation, PositionSnapshot afterMove,
      List<PositionSnapshot> afterHistory) {
    if (engineSettings == null || !ComputerEngineIds.KNIGHTSHADE.equals(engine.descriptor().id())) return null;
    PonderSettings settings = engineSettings.ponderSettings();
    if (!settings.enabled() || !settings.speculativeWorkerEnabled()) return null;
    String opponentId = side == PieceColor.WHITE
        ? runtime.configuration.blackEngineId() : runtime.configuration.whiteEngineId();
    int realWorkers = providers.get(opponentId).estimatedSearchWorkers();
    if (availableProcessors - Math.max(1, realWorkers) < 2) return null;
    return new PonderRequest(runtime.game.id(), generation, afterMove,
        afterHistory, settings, "knightshade-default-v1", true);
  }
  private boolean expire(Runtime runtime) { if (runtime.game.result().isPresent() || runtime.stopped || runtime.turnStartedAt == null || !runtime.game.currentClock().timed()) return false; Duration remaining = runtime.game.currentClock().remaining(runtime.game.currentTurn()).orElseThrow(); if (elapsed(runtime).compareTo(remaining) < 0) return false; runtime.searchVersion++; runtime.game.timeout(runtime.game.currentTurn()); runtime.phase = ComputerGamePhase.FINISHED; runtime.turnStartedAt = null; runtime.message = Optional.of("Time expired"); return true; }
  private void cancelSearches(Runtime runtime) { runtime.white.cancelSearch(); runtime.black.cancelSearch(); runtime.white.cancelPonder(); runtime.black.cancelPonder(); }
  private Duration permitted(Runtime r) {
    Duration configured = r.game.currentTurn() == PieceColor.WHITE
        ? r.configuration.whiteThinkingTime() : r.configuration.blackThinkingTime();
    if (!r.game.currentClock().timed()) return configured;
    Duration left = r.game.currentClock().remaining(r.game.currentTurn()).orElseThrow().minus(elapsed(r));
    return left.isPositive() ? (left.compareTo(configured) < 0 ? left : configured) : Duration.ofMillis(1);
  }
  private Duration elapsed(Runtime r) { Duration value = Duration.between(r.turnStartedAt, clock.instant()); return value.isNegative() ? Duration.ZERO : value; }
  private ComputerVsComputerGameState snapshot(Runtime r) { GameClockSnapshot displayed = r.game.currentClock(); if (displayed.timed() && r.game.result().isEmpty() && !r.stopped && r.turnStartedAt != null) { Duration remaining = displayed.remaining(r.game.currentTurn()).orElseThrow(); Duration current = remaining.minus(elapsed(r)); displayed = r.game.currentTurn() == PieceColor.WHITE ? new GameClockSnapshot(Optional.of(current.isNegative() ? Duration.ZERO : current), displayed.blackRemaining()) : new GameClockSnapshot(displayed.whiteRemaining(), Optional.of(current.isNegative() ? Duration.ZERO : current)); } return new ComputerVsComputerGameState(r.game.id(), r.game.whitePlayer().orElseThrow(), r.game.blackPlayer().orElseThrow(), r.whiteDescriptor, r.blackDescriptor, r.game.initialPosition(), r.game.currentPosition(), r.game.moveHistory(), displayed, r.game.timeControl(), r.phase, r.game.result(), r.game.terminationReason(), r.stopped, r.message); }
  private ComputerMoveEngineProvider provider(String id) { ComputerMoveEngineProvider value = providers.get(id); if (value == null) throw new NoSuchElementException("Unknown computer engine: " + id); return value; }
  private Runtime runtime(GameId id) { Runtime value = runtimes.get(Objects.requireNonNull(id)); if (value == null) throw new NoSuchElementException("No computer-versus-computer runtime for game: " + id); return value; }
  private static String detail(Throwable failure) { Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause(); return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(); }
  private static final class Runtime { final ChessGame game; final ComputerVsComputerConfiguration configuration; final ComputerEngineDescriptor whiteDescriptor, blackDescriptor; final ComputerMoveEngine white, black; long searchVersion; long whitePonderGeneration, blackPonderGeneration; Instant turnStartedAt; ComputerGamePhase phase = ComputerGamePhase.STARTING; boolean stopped; Optional<String> message = Optional.empty(); Runtime(ChessGame game, ComputerVsComputerConfiguration configuration, ComputerEngineDescriptor whiteDescriptor, ComputerEngineDescriptor blackDescriptor, ComputerMoveEngine white, ComputerMoveEngine black) { this.game=game; this.configuration=configuration; this.whiteDescriptor=whiteDescriptor; this.blackDescriptor=blackDescriptor; this.white=white; this.black=black; } }
}
