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
  private final Map<GameId, Runtime> runtimes = new ConcurrentHashMap<>();
  private final ScheduledExecutorService moveScheduler =
      Executors.newSingleThreadScheduledExecutor(
          task -> {
            Thread thread = new Thread(task, "lastmove-engine-match");
            thread.setDaemon(true);
            return thread;
          });

  public ComputerVsComputerGameService(ChessGameFactory games, List<ComputerMoveEngineProvider> providers, Clock clock) {
    this.games = Objects.requireNonNull(games); this.clock = Objects.requireNonNull(clock);
    this.providers = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(p -> p.descriptor().id(), p -> p));
  }
  public List<ComputerEngineDescriptor> availableEngines() { return providers.values().stream().map(ComputerMoveEngineProvider::descriptor).sorted(Comparator.comparing(ComputerEngineDescriptor::displayName)).toList(); }
  public List<ComputerVsComputerGameState> gamesInMemory() { return runtimes.keySet().stream().map(this::state).toList(); }
  public CompletionStage<ComputerVsComputerGameState> createGame(ComputerVsComputerConfiguration configuration) {
    ComputerMoveEngineProvider whiteProvider = provider(configuration.whiteEngineId());
    ComputerMoveEngineProvider blackProvider = provider(configuration.blackEngineId());
    ChessGame game = games.createInitial(new GamePlayer(whiteProvider.descriptor().displayName(), PieceColor.WHITE), new GamePlayer(blackProvider.descriptor().displayName(), PieceColor.BLACK), Optional.of(configuration.timeControl()));
    Runtime runtime = new Runtime(game, configuration, whiteProvider.descriptor(), blackProvider.descriptor(), whiteProvider.create(), blackProvider.create());
    runtimes.put(game.id(), runtime);
    return runtime.white.start().thenCompose(ignored -> runtime.black.start()).thenApply(ignored -> {
      synchronized (runtime) {
        runtime.turnStartedAt = clock.instant();
        runtime.phase = ComputerGamePhase.ENGINE_THINKING;
        requestMove(runtime);
        return snapshot(runtime);
      }
    });
  }
  public ComputerVsComputerGameState state(GameId id) { Runtime runtime = runtime(id); synchronized (runtime) { expire(runtime); return snapshot(runtime); } }
  public GameRecord gameRecord(GameId id) { return runtime(id).game.toRecord(); }
  /** Stops the match without assigning either player a result. */
  public ComputerVsComputerGameState stop(GameId id) { Runtime runtime = runtime(id); synchronized (runtime) { runtime.searchVersion++; runtime.white.cancelSearch(); runtime.black.cancelSearch(); runtime.phase = ComputerGamePhase.FINISHED; runtime.stopped = true; runtime.turnStartedAt = null; runtime.message = Optional.of("Game stopped"); return snapshot(runtime); } }
  public CompletionStage<ComputerVsComputerGameState> restartGame(GameId id) { Runtime old = runtime(id); ComputerVsComputerConfiguration config = old.configuration; closeGame(id); return createGame(config); }
  public boolean closeGame(GameId id) { Runtime runtime = runtimes.remove(id); if (runtime == null) return false; synchronized (runtime) { runtime.searchVersion++; runtime.white.cancelSearch(); runtime.black.cancelSearch(); runtime.white.close(); runtime.black.close(); } return true; }
  @PreDestroy void closeAll() { moveScheduler.shutdownNow(); new ArrayList<>(runtimes.keySet()).forEach(this::closeGame); }
  /** Starts one engine turn and schedules the next turn after its result.
   *
   * <p>This deliberately does not compose the complete match into one future: the caller must get
   * the initial snapshot immediately so JavaFX can reveal the live board between engine moves.
   */
  private void requestMove(Runtime runtime) {
    final PositionSnapshot position; final long version; final Duration limit; final ComputerMoveEngine engine;
    synchronized (runtime) { expire(runtime); if (runtime.game.result().isPresent() || runtime.stopped) return; position = runtime.game.currentPosition(); version = ++runtime.searchVersion; engine = runtime.game.currentTurn() == PieceColor.WHITE ? runtime.white : runtime.black; limit = permitted(runtime); }
    engine.chooseMove(new ComputerMoveRequest(position, limit, runtime.game.positionHistory())).handle((move, failure) -> {
      boolean continueMatch;
      synchronized (runtime) {
        if (version != runtime.searchVersion) return null;
        if (failure != null) { runtime.phase = ComputerGamePhase.ENGINE_ERROR; runtime.turnStartedAt = null; runtime.message = Optional.of("Computer engine error: " + detail(failure)); return null; }
        expire(runtime); if (runtime.game.result().isPresent() || runtime.stopped || !runtime.game.currentPosition().equals(position)) return null;
        var applied = runtime.game.move(move, elapsed(runtime));
        if (!applied.accepted()) { runtime.phase = ComputerGamePhase.ENGINE_ERROR; runtime.message = Optional.of("The engine returned an illegal move: " + move); return null; }
        runtime.turnStartedAt = clock.instant(); runtime.phase = runtime.game.result().isPresent() ? ComputerGamePhase.FINISHED : ComputerGamePhase.ENGINE_THINKING;
        continueMatch = runtime.phase == ComputerGamePhase.ENGINE_THINKING;
      }
      if (continueMatch) {
        long delayMillis = runtime.configuration.moveDelay().toMillis();
        moveScheduler.schedule(() -> requestMove(runtime), delayMillis, TimeUnit.MILLISECONDS);
      }
      return null;
    });
  }
  private void expire(Runtime runtime) { if (runtime.game.result().isPresent() || runtime.stopped || runtime.turnStartedAt == null || !runtime.game.currentClock().timed()) return; Duration remaining = runtime.game.currentClock().remaining(runtime.game.currentTurn()).orElseThrow(); if (elapsed(runtime).compareTo(remaining) < 0) return; runtime.searchVersion++; runtime.white.cancelSearch(); runtime.black.cancelSearch(); runtime.game.timeout(runtime.game.currentTurn()); runtime.phase = ComputerGamePhase.FINISHED; runtime.turnStartedAt = null; runtime.message = Optional.of("Time expired"); }
  private Duration permitted(Runtime r) { if (!r.game.currentClock().timed()) return r.configuration.thinkingTime(); Duration left = r.game.currentClock().remaining(r.game.currentTurn()).orElseThrow().minus(elapsed(r)); return left.isPositive() ? (left.compareTo(r.configuration.thinkingTime()) < 0 ? left : r.configuration.thinkingTime()) : Duration.ofMillis(1); }
  private Duration elapsed(Runtime r) { Duration value = Duration.between(r.turnStartedAt, clock.instant()); return value.isNegative() ? Duration.ZERO : value; }
  private ComputerVsComputerGameState snapshot(Runtime r) { GameClockSnapshot displayed = r.game.currentClock(); if (displayed.timed() && r.game.result().isEmpty() && !r.stopped && r.turnStartedAt != null) { Duration remaining = displayed.remaining(r.game.currentTurn()).orElseThrow(); Duration current = remaining.minus(elapsed(r)); displayed = r.game.currentTurn() == PieceColor.WHITE ? new GameClockSnapshot(Optional.of(current.isNegative() ? Duration.ZERO : current), displayed.blackRemaining()) : new GameClockSnapshot(displayed.whiteRemaining(), Optional.of(current.isNegative() ? Duration.ZERO : current)); } return new ComputerVsComputerGameState(r.game.id(), r.game.whitePlayer().orElseThrow(), r.game.blackPlayer().orElseThrow(), r.whiteDescriptor, r.blackDescriptor, r.game.initialPosition(), r.game.currentPosition(), r.game.moveHistory(), displayed, r.phase, r.game.result(), r.game.terminationReason(), r.stopped, r.message); }
  private ComputerMoveEngineProvider provider(String id) { ComputerMoveEngineProvider value = providers.get(id); if (value == null) throw new NoSuchElementException("Unknown computer engine: " + id); return value; }
  private Runtime runtime(GameId id) { Runtime value = runtimes.get(Objects.requireNonNull(id)); if (value == null) throw new NoSuchElementException("No computer-versus-computer runtime for game: " + id); return value; }
  private static String detail(Throwable failure) { Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause(); return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(); }
  private static final class Runtime { final ChessGame game; final ComputerVsComputerConfiguration configuration; final ComputerEngineDescriptor whiteDescriptor, blackDescriptor; final ComputerMoveEngine white, black; long searchVersion; Instant turnStartedAt; ComputerGamePhase phase = ComputerGamePhase.STARTING; boolean stopped; Optional<String> message = Optional.empty(); Runtime(ChessGame game, ComputerVsComputerConfiguration configuration, ComputerEngineDescriptor whiteDescriptor, ComputerEngineDescriptor blackDescriptor, ComputerMoveEngine white, ComputerMoveEngine black) { this.game=game; this.configuration=configuration; this.whiteDescriptor=whiteDescriptor; this.blackDescriptor=blackDescriptor; this.white=white; this.black=black; } }
}
