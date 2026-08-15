package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.computer.ComputerGamePhase;
import com.escontrela.lastmove.application.computer.ComputerGameState;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.repository.ProgressiveGameRepository;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.GameClockSnapshot;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GameRecord;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.Player;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.TakebackRequest;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Application use case coordinating one human against an asynchronous computer opponent.
 *
 * <p>The authoritative position, official line, clocks, takeback and result remain in {@link
 * ChessGame}. This service owns only application-runtime concerns: engine lifecycle, whose input is
 * currently expected, elapsed wall-clock time and stale asynchronous-search protection.
 */
@Service
public final class ComputerGameService {

  private final ProgressiveGameRepository gameRepository;
  private final ChessGameFactory gameFactory;
  private final Map<String, ComputerMoveEngineProvider> engineProviders;
  private final Clock clock;
  private final Map<GameId, RuntimeContext> runtimes = new ConcurrentHashMap<>();

  public ComputerGameService(
      ProgressiveGameRepository gameRepository,
      ChessGameFactory gameFactory,
      List<ComputerMoveEngineProvider> engineProviders,
      Clock clock) {
    this.gameRepository =
        Objects.requireNonNull(gameRepository, "gameRepository must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    Objects.requireNonNull(engineProviders, "engineProviders must not be null");
    this.engineProviders =
        engineProviders.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    provider -> provider.descriptor().id(), provider -> provider));
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /** Creates a progressive game, starts its engine and plays the opening engine move if necessary. */
  public CompletionStage<ComputerGameState> createGame(ComputerGameConfiguration configuration) {
    ComputerGameConfiguration required =
        Objects.requireNonNull(configuration, "configuration must not be null");
    ComputerMoveEngineProvider provider = provider(required.engineId());
    ComputerMoveEngine engine = provider.create();
    PieceColor computerColor = required.humanColor().opposite();
    Player human = new Player(required.humanName(), required.humanColor());
    Player computer = new Player(provider.descriptor().displayName(), computerColor);
    Player white = required.humanColor() == PieceColor.WHITE ? human : computer;
    Player black = required.humanColor() == PieceColor.BLACK ? human : computer;
    ChessGame game =
        gameFactory.createInitial(white, black, Optional.of(required.timeControl()));
    RuntimeContext context = new RuntimeContext(required, engine, provider.descriptor());
    gameRepository.save(game);
    runtimes.put(game.id(), context);

    return engine
        .start()
        .handle(
            (ignored, failure) -> {
              synchronized (context) {
                if (failure != null) {
                  markEngineFailure(context, failure);
                  return false;
                }
                context.turnStartedAt = clock.instant();
                context.phase =
                    game.currentTurn() == required.humanColor()
                        ? ComputerGamePhase.WAITING_FOR_HUMAN
                        : ComputerGamePhase.ENGINE_THINKING;
                return context.phase == ComputerGamePhase.ENGINE_THINKING;
              }
            })
        .thenCompose(
            computerStarts ->
                computerStarts
                    ? requestComputerMove(game, context)
                    : CompletableFuture.completedFuture(stateOf(game, context)));
  }

  /** Lists the computer opponents available to the game-setup UI. */
  public List<ComputerEngineDescriptor> availableEngines() {
    return engineProviders.values().stream()
        .map(ComputerMoveEngineProvider::descriptor)
        .sorted(java.util.Comparator.comparing(ComputerEngineDescriptor::displayName))
        .toList();
  }

  /** Applies a human move and, when accepted, completes after the computer has replied. */
  public CompletionStage<ComputerGameState> playHumanMove(GameId gameId, MoveCommand command) {
    ChessGame game = game(gameId);
    RuntimeContext context = runtime(gameId);
    synchronized (context) {
      expireClockIfNecessary(game, context);
      if (game.result().isPresent()) {
        return CompletableFuture.completedFuture(stateOf(game, context));
      }
      if (context.phase != ComputerGamePhase.WAITING_FOR_HUMAN
          || game.currentTurn() != context.configuration.humanColor()) {
        throw new IllegalStateException("The game is not waiting for a human move");
      }
      Duration elapsed = elapsedSinceTurnStarted(context);
      var result = game.move(Objects.requireNonNull(command, "command must not be null"), elapsed);
      if (!result.accepted()) {
        context.message = result.rejectionReason();
        return CompletableFuture.completedFuture(stateOf(game, context));
      }
      gameRepository.save(game);
      context.message = Optional.empty();
      context.turnStartedAt = clock.instant();
      if (game.result().isPresent()) {
        context.phase = ComputerGamePhase.FINISHED;
        return CompletableFuture.completedFuture(stateOf(game, context));
      }
      context.phase = ComputerGamePhase.ENGINE_THINKING;
    }
    return requestComputerMove(game, context);
  }

  /** Returns the latest DTO, applying a clock loss first when the active clock reached zero. */
  public ComputerGameState state(GameId gameId) {
    ChessGame game = game(gameId);
    RuntimeContext context = runtime(gameId);
    synchronized (context) {
      expireClockIfNecessary(game, context);
      return stateOf(game, context);
    }
  }

  /** Automatically accepts the human player's takeback and restores one complete human turn. */
  public ComputerGameState takeBack(GameId gameId) {
    ChessGame game = game(gameId);
    RuntimeContext context = runtime(gameId);
    synchronized (context) {
      cancelCurrentSearch(context);
      int pliesToUndo = pliesToUndo(game, context.configuration.humanColor());
      TakebackRequest request =
          game.requestTakeback(context.configuration.humanColor(), pliesToUndo);
      request.accept(context.configuration.humanColor().opposite());
      game.takeBack(request);
      gameRepository.save(game);
      context.phase = ComputerGamePhase.WAITING_FOR_HUMAN;
      context.turnStartedAt = clock.instant();
      context.message = Optional.of("Takeback accepted by " + context.descriptor.displayName());
      return stateOf(game, context);
    }
  }

  /** Finishes the selected game as a human resignation and cancels any active engine search. */
  public ComputerGameState resign(GameId gameId) {
    ChessGame game = game(gameId);
    RuntimeContext context = runtime(gameId);
    synchronized (context) {
      cancelCurrentSearch(context);
      if (game.result().isEmpty()) {
        game.resign(context.configuration.humanColor());
        gameRepository.save(game);
      }
      context.phase = ComputerGamePhase.FINISHED;
      context.turnStartedAt = null;
      context.message = Optional.of(context.configuration.humanName() + " resigned");
      return stateOf(game, context);
    }
  }

  /** Exports the completed or in-progress official line for later analysis. */
  public GameRecord gameRecord(GameId gameId) {
    return game(gameId).toRecord();
  }

  /** Stops the engine and removes a progressive game from the process-local catalogue. */
  public boolean closeGame(GameId gameId) {
    GameId required = Objects.requireNonNull(gameId, "gameId must not be null");
    RuntimeContext context = runtimes.remove(required);
    if (context != null) {
      synchronized (context) {
        cancelCurrentSearch(context);
        context.engine.close();
      }
    }
    return gameRepository.deleteById(required);
  }

  @PreDestroy
  void closeEngines() {
    runtimes.values().forEach(context -> context.engine.close());
    runtimes.clear();
  }

  private CompletionStage<ComputerGameState> requestComputerMove(
      ChessGame game, RuntimeContext context) {
    final long searchVersion;
    final PositionSnapshot searchedPosition;
    final Duration thinkingTime;
    synchronized (context) {
      expireClockIfNecessary(game, context);
      if (game.result().isPresent()) {
        return CompletableFuture.completedFuture(stateOf(game, context));
      }
      searchedPosition = game.currentPosition();
      thinkingTime = permittedEngineThinkingTime(game, context);
      searchVersion = ++context.searchVersion;
    }
    return context.engine
        .chooseMove(new ComputerMoveRequest(searchedPosition, thinkingTime))
        .handle(
            (move, failure) -> {
              synchronized (context) {
                if (searchVersion != context.searchVersion) {
                  return stateOf(game, context);
                }
                if (failure != null) {
                  markEngineFailure(context, failure);
                  return stateOf(game, context);
                }
                expireClockIfNecessary(game, context);
                if (game.result().isPresent()) {
                  return stateOf(game, context);
                }
                if (!game.currentPosition().equals(searchedPosition)) {
                  return stateOf(game, context);
                }
                Duration elapsed = elapsedSinceTurnStarted(context);
                var result = game.move(move, elapsed);
                if (!result.accepted()) {
                  context.phase = ComputerGamePhase.ENGINE_ERROR;
                  context.turnStartedAt = null;
                  context.message =
                      Optional.of("The engine returned an illegal move: " + move);
                  return stateOf(game, context);
                }
                gameRepository.save(game);
                context.message = Optional.empty();
                context.turnStartedAt = clock.instant();
                context.phase =
                    game.result().isPresent()
                        ? ComputerGamePhase.FINISHED
                        : ComputerGamePhase.WAITING_FOR_HUMAN;
                return stateOf(game, context);
              }
            });
  }

  private void expireClockIfNecessary(ChessGame game, RuntimeContext context) {
    if (game.result().isPresent() || context.turnStartedAt == null || !game.currentClock().timed()) {
      return;
    }
    Duration remaining = game.currentClock().remaining(game.currentTurn()).orElseThrow();
    if (elapsedSinceTurnStarted(context).compareTo(remaining) < 0) {
      return;
    }
    cancelCurrentSearch(context);
    game.timeout(game.currentTurn());
    gameRepository.save(game);
    context.phase = ComputerGamePhase.FINISHED;
    context.turnStartedAt = null;
    context.message = Optional.of("Time expired");
  }

  private ComputerGameState stateOf(ChessGame game, RuntimeContext context) {
    PieceColor humanColor = context.configuration.humanColor();
    List<Ply> moves = game.moveHistory();
    return new ComputerGameState(
        game.id(),
        game.whitePlayer().orElseThrow(),
        game.blackPlayer().orElseThrow(),
        humanColor,
        context.descriptor,
        game.currentPosition(),
        moves,
        game.currentState(),
        displayedClock(game, context),
        context.phase,
        game.result(),
        game.terminationReason(),
        context.phase == ComputerGamePhase.WAITING_FOR_HUMAN
            && game.currentTurn() == humanColor
            && game.result().isEmpty(),
        canTakeBack(moves, humanColor),
        context.message);
  }

  private GameClockSnapshot displayedClock(ChessGame game, RuntimeContext context) {
    GameClockSnapshot stored = game.currentClock();
    if (!stored.timed() || game.result().isPresent() || context.turnStartedAt == null) {
      return stored;
    }
    PieceColor active = game.currentTurn();
    Duration remaining = stored.remaining(active).orElseThrow();
    Duration displayed = remaining.minus(min(remaining, elapsedSinceTurnStarted(context)));
    return active == PieceColor.WHITE
        ? new GameClockSnapshot(Optional.of(displayed), stored.blackRemaining())
        : new GameClockSnapshot(stored.whiteRemaining(), Optional.of(displayed));
  }

  private Duration permittedEngineThinkingTime(ChessGame game, RuntimeContext context) {
    if (!game.currentClock().timed()) {
      return context.configuration.engineThinkingTime();
    }
    Duration remaining = game.currentClock().remaining(game.currentTurn()).orElseThrow();
    Duration available = remaining.minus(min(remaining, elapsedSinceTurnStarted(context)));
    if (available.isZero()) {
      return Duration.ofMillis(1);
    }
    return min(available, context.configuration.engineThinkingTime());
  }

  private Duration elapsedSinceTurnStarted(RuntimeContext context) {
    if (context.turnStartedAt == null) {
      return Duration.ZERO;
    }
    Duration elapsed = Duration.between(context.turnStartedAt, clock.instant());
    return elapsed.isNegative() ? Duration.ZERO : elapsed;
  }

  private void cancelCurrentSearch(RuntimeContext context) {
    context.searchVersion++;
    context.engine.cancelSearch();
  }

  private void markEngineFailure(RuntimeContext context, Throwable failure) {
    context.engine.close();
    context.phase = ComputerGamePhase.ENGINE_ERROR;
    context.turnStartedAt = null;
    Throwable cause = rootCause(failure);
    String detail = cause.getMessage();
    context.message =
        Optional.of(
            "Computer engine error: "
                + (detail == null || detail.isBlank()
                    ? cause.getClass().getSimpleName()
                    : detail));
  }

  private int pliesToUndo(ChessGame game, PieceColor humanColor) {
    List<Ply> history = game.moveHistory();
    if (history.isEmpty()) {
      throw new IllegalStateException("There is no human move to take back");
    }
    if (history.getLast().movingColor() == humanColor) {
      return 1;
    }
    if (history.size() >= 2 && history.get(history.size() - 2).movingColor() == humanColor) {
      return 2;
    }
    throw new IllegalStateException("There is no human move to take back");
  }

  private boolean canTakeBack(List<Ply> moves, PieceColor humanColor) {
    return !moves.isEmpty()
        && (moves.getLast().movingColor() == humanColor
            || (moves.size() >= 2 && moves.get(moves.size() - 2).movingColor() == humanColor));
  }

  private ComputerMoveEngineProvider provider(String engineId) {
    ComputerMoveEngineProvider provider = engineProviders.get(engineId);
    if (provider == null) {
      throw new NoSuchElementException("Unknown computer engine: " + engineId);
    }
    return provider;
  }

  private ChessGame game(GameId gameId) {
    GameId required = Objects.requireNonNull(gameId, "gameId must not be null");
    return gameRepository
        .findById(required)
        .orElseThrow(() -> new NoSuchElementException("Unknown progressive game: " + required));
  }

  private RuntimeContext runtime(GameId gameId) {
    RuntimeContext context = runtimes.get(Objects.requireNonNull(gameId, "gameId must not be null"));
    if (context == null) {
      throw new NoSuchElementException("No computer runtime for progressive game: " + gameId);
    }
    return context;
  }

  private static Duration min(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  private static final class RuntimeContext {
    private final ComputerGameConfiguration configuration;
    private final ComputerMoveEngine engine;
    private final ComputerEngineDescriptor descriptor;
    private ComputerGamePhase phase = ComputerGamePhase.STARTING;
    private Instant turnStartedAt;
    private long searchVersion;
    private Optional<String> message = Optional.empty();

    private RuntimeContext(
        ComputerGameConfiguration configuration,
        ComputerMoveEngine engine,
        ComputerEngineDescriptor descriptor) {
      this.configuration = configuration;
      this.engine = engine;
      this.descriptor = descriptor;
    }
  }
}
