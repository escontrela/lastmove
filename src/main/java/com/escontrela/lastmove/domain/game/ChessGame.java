package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Aggregate root for one progressive chess game with a single official move line.
 *
 * <p>The aggregate accepts either a coordinate-based {@link MoveCommand} or a SAN move through
 * {@link #move(SanMove)} and delegates both alternatives to an injected {@link ChessRulesEngine}.
 * Both inputs share the same state transition and invariants. The aggregate owns the authoritative
 * current position and only appends a ply after an accepted result. Analysis navigation and
 * variations deliberately belong to a separate analysis aggregate.
 */
public final class ChessGame {

  private final GameId id;
  private final PositionSnapshot initialPosition;
  private final Optional<GamePlayer> whitePlayer;
  private final Optional<GamePlayer> blackPlayer;
  private final Optional<TimeControl> timeControl;
  private final ChessRulesEngine rulesEngine;
  private final List<Ply> moveHistory;
  private final List<GameClockSnapshot> clocksBeforeMoves;
  private PositionSnapshot currentPosition;
  private GameClockSnapshot currentClock;
  private GameResult result;
  private GameTerminationReason terminationReason;

  ChessGame(
      GameId id,
      PositionSnapshot initialPosition,
      PositionSnapshot currentPosition,
      List<Ply> moveHistory,
      List<GameClockSnapshot> clocksBeforeMoves,
      GameClockSnapshot currentClock,
      Optional<GamePlayer> whitePlayer,
      Optional<GamePlayer> blackPlayer,
      Optional<TimeControl> timeControl,
      Optional<GameResult> result,
      Optional<GameTerminationReason> terminationReason,
      ChessRulesEngine rulesEngine) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.initialPosition =
        Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    this.currentPosition =
        Objects.requireNonNull(currentPosition, "currentPosition must not be null");
    this.moveHistory =
        new ArrayList<>(Objects.requireNonNull(moveHistory, "moveHistory must not be null"));
    this.clocksBeforeMoves =
        new ArrayList<>(
            Objects.requireNonNull(clocksBeforeMoves, "clocksBeforeMoves must not be null"));
    this.currentClock = Objects.requireNonNull(currentClock, "currentClock must not be null");
    this.whitePlayer = Objects.requireNonNull(whitePlayer, "whitePlayer must not be null");
    this.blackPlayer = Objects.requireNonNull(blackPlayer, "blackPlayer must not be null");
    this.timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    this.result = Objects.requireNonNull(result, "result must not be null").orElse(null);
    this.terminationReason =
        Objects.requireNonNull(terminationReason, "terminationReason must not be null").orElse(null);
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    validatePlayers();
    validateHistory();
    validateClocks();
    validateTerminalState();
  }

  /**
   * Executes a requested move and advances the official line only when the result is accepted.
   *
   * <p>A rejected result never mutates the game. Once the position is terminal, every later move
   * is rejected without invoking the rules engine.
   */
  public MoveExecutionResult move(MoveCommand command) {
    return move(command, Duration.ZERO);
  }

  /** Executes a move expressed in Standard Algebraic Notation. */
  public MoveExecutionResult move(SanMove move) {
    return move(move, Duration.ZERO);
  }

  /** Convenience overload for callers that receive SAN as text. */
  public MoveExecutionResult move(String san) {
    return move(SanMove.of(san));
  }

  /**
   * Executes a move after the supplied thinking time and updates the progressive-game clock.
   *
   * <p>Untimed games ignore elapsed time. A timed accepted move subtracts elapsed time and adds the
   * configured increment. Rejected moves preserve both position and clock.
   */
  public MoveExecutionResult move(MoveCommand command, Duration elapsed) {
    MoveCommand required = Objects.requireNonNull(command, "command must not be null");
    return executeMove(
        elapsed, position -> rulesEngine.execute(position, required));
  }

  /** Executes a SAN move after the supplied thinking time and updates the game clock. */
  public MoveExecutionResult move(SanMove move, Duration elapsed) {
    SanMove required = Objects.requireNonNull(move, "move must not be null");
    return executeMove(
        elapsed, position -> rulesEngine.execute(position, required));
  }

  /** Convenience overload for timed moves received as SAN text. */
  public MoveExecutionResult move(String san, Duration elapsed) {
    return move(SanMove.of(san), elapsed);
  }

  private MoveExecutionResult executeMove(
      Duration elapsed,
      Function<PositionSnapshot, MoveExecutionResult> executionRequest) {
    Duration requiredElapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
    Objects.requireNonNull(executionRequest, "executionRequest must not be null");
    if (result != null) {
      return MoveExecutionResult.rejected(currentPosition, "The game has already finished");
    }

    PositionSnapshot previousPosition = currentPosition;
    MoveExecutionResult execution = executionRequest.apply(previousPosition);
    if (!execution.accepted()) {
      if (!previousPosition.equals(execution.newSnapshot())) {
        throw new IllegalStateException("A rejected move must preserve the current position");
      }
      return execution;
    }

    MoveDescriptor descriptor =
        execution
            .move()
            .orElseThrow(() -> new IllegalStateException("An accepted move requires a descriptor"));
    validateAcceptedResult(execution);
    GameClockSnapshot nextClock =
        currentClock.afterMove(
            previousPosition.activeColor(),
            requiredElapsed,
            timeControl.map(TimeControl::increment).orElse(Duration.ZERO));
    clocksBeforeMoves.add(currentClock);
    moveHistory.add(
        new Ply(
            UUID.randomUUID(),
            descriptor,
            execution.newSnapshot(),
            previousPosition.fullmoveNumber(),
            previousPosition.activeColor()));
    currentPosition = execution.newSnapshot();
    currentClock = nextClock;
    result = terminalResult(currentPosition).orElse(null);
    terminationReason = terminalReason(currentPosition).orElse(null);
    return execution;
  }

  /** Finishes the game immediately because one player resigned. */
  public GameResult resign(PieceColor resignedBy) {
    PieceColor required = Objects.requireNonNull(resignedBy, "resignedBy must not be null");
    requireGameInProgress();
    result = winFor(required.opposite());
    terminationReason = GameTerminationReason.RESIGNATION;
    return result;
  }

  /** Finishes a timed game because the active player's clock reached zero. */
  public GameResult timeout(PieceColor expiredPlayer) {
    PieceColor required =
        Objects.requireNonNull(expiredPlayer, "expiredPlayer must not be null");
    requireGameInProgress();
    if (currentTurn() != required) {
      throw new IllegalArgumentException("only the active player's clock can expire");
    }
    currentClock = currentClock.expired(required);
    result = winFor(required.opposite());
    terminationReason = GameTerminationReason.TIMEOUT;
    return result;
  }

  /**
   * Creates a takeback request anchored to the current last move.
   *
   * <p>The earliest move being rectified must belong to the requester. This supports undoing the
   * requester's last ply alone or the requester's ply plus a later opponent reply.
   */
  public TakebackRequest requestTakeback(PieceColor requestedBy, int pliesToUndo) {
    PieceColor requester = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
    if (whitePlayer.isEmpty() || blackPlayer.isEmpty()) {
      throw new IllegalStateException("A takeback requires registered progressive-game players");
    }
    if (pliesToUndo < 1 || pliesToUndo > moveHistory.size()) {
      throw new IllegalArgumentException("pliesToUndo must reference existing moves");
    }
    Ply earliestUndone = moveHistory.get(moveHistory.size() - pliesToUndo);
    if (earliestUndone.movingColor() != requester) {
      throw new IllegalArgumentException("The earliest rectified move must belong to the requester");
    }
    return new TakebackRequest(id, requester, pliesToUndo, moveHistory.getLast().id());
  }

  /**
   * Applies a takeback accepted by the opponent and restores position, result and clock.
   *
   * @return the restored current position
   */
  public PositionSnapshot takeBack(TakebackRequest request) {
    TakebackRequest required = Objects.requireNonNull(request, "request must not be null");
    if (!id.equals(required.gameId())) {
      throw new IllegalArgumentException("The takeback belongs to another game");
    }
    if (required.status() != TakebackStatus.ACCEPTED) {
      throw new IllegalStateException("The opponent must accept the takeback first");
    }
    if (moveHistory.isEmpty()
        || !moveHistory.getLast().id().equals(required.expectedLastPlyId())) {
      throw new IllegalStateException("The game advanced after the takeback was requested");
    }
    if (required.pliesToUndo() > moveHistory.size()) {
      throw new IllegalStateException("The requested moves are no longer available");
    }
    Ply earliestUndone = moveHistory.get(moveHistory.size() - required.pliesToUndo());
    if (earliestUndone.movingColor() != required.requestedBy()) {
      throw new IllegalStateException("The takeback no longer matches the requester's move");
    }

    for (int count = 0; count < required.pliesToUndo(); count++) {
      moveHistory.removeLast();
      currentClock = clocksBeforeMoves.removeLast();
    }
    currentPosition =
        moveHistory.isEmpty() ? initialPosition : moveHistory.getLast().resultingPosition();
    result = terminalResult(currentPosition).orElse(null);
    terminationReason = terminalReason(currentPosition).orElse(null);
    required.markApplied();
    return currentPosition;
  }

  public GameId id() {
    return id;
  }

  public PositionSnapshot initialPosition() {
    return initialPosition;
  }

  public PositionSnapshot currentPosition() {
    return currentPosition;
  }

  public Optional<GamePlayer> whitePlayer() {
    return whitePlayer;
  }

  public Optional<GamePlayer> blackPlayer() {
    return blackPlayer;
  }

  public Optional<TimeControl> timeControl() {
    return timeControl;
  }

  /** Returns the immutable current clock state. */
  public GameClockSnapshot currentClock() {
    return currentClock;
  }

  /** Returns an immutable view of the single official move line. */
  public List<Ply> moveHistory() {
    return List.copyOf(moveHistory);
  }

  public Optional<MoveDescriptor> lastMove() {
    return currentPosition.lastMove();
  }

  public PieceColor currentTurn() {
    return currentPosition.activeColor();
  }

  public boolean isCheck() {
    return currentPosition.check();
  }

  public boolean isCheckmate() {
    return currentPosition.mate();
  }

  public boolean isStalemate() {
    return currentPosition.stalemate();
  }

  public Optional<GameResult> result() {
    return Optional.ofNullable(result);
  }

  public Optional<GameTerminationReason> terminationReason() {
    return Optional.ofNullable(terminationReason);
  }

  /** Exports an immutable record without the injected rules engine or aggregate mutability. */
  public GameRecord toRecord() {
    List<RecordedPly> recordedMoves = new ArrayList<>(moveHistory.size());
    for (int index = 0; index < moveHistory.size(); index++) {
      GameClockSnapshot clockAfter =
          index + 1 < clocksBeforeMoves.size()
              ? clocksBeforeMoves.get(index + 1)
              : currentClock;
      recordedMoves.add(
          new RecordedPly(moveHistory.get(index), clocksBeforeMoves.get(index), clockAfter));
    }
    return new GameRecord(
        id,
        displayTitle(),
        initialPosition,
        whitePlayer,
        blackPlayer,
        timeControl,
        recordedMoves,
        Optional.ofNullable(result),
        Optional.ofNullable(terminationReason));
  }

  /** Returns the current rules state without exposing independently mutable state. */
  public GameStateSnapshot currentState() {
    return new GameStateSnapshot(
        currentPosition.activeColor(),
        currentPosition.castlingRights(),
        currentPosition.enPassantTarget(),
        currentPosition.halfmoveClock(),
        currentPosition.fullmoveNumber(),
        currentPosition.check(),
        currentPosition.mate(),
        currentPosition.stalemate(),
        Optional.ofNullable(result));
  }

  private void validatePlayers() {
    whitePlayer.ifPresent(
        player -> {
          if (player.getColor() != PieceColor.WHITE) {
            throw new IllegalArgumentException("whitePlayer must play White");
          }
        });
    blackPlayer.ifPresent(
        player -> {
          if (player.getColor() != PieceColor.BLACK) {
            throw new IllegalArgumentException("blackPlayer must play Black");
          }
        });
  }

  private void validateHistory() {
    if (moveHistory.size() != clocksBeforeMoves.size()) {
      throw new IllegalArgumentException("Every played ply requires its preceding clock state");
    }
    if (moveHistory.isEmpty()) {
      if (!initialPosition.equals(currentPosition)) {
        throw new IllegalArgumentException("A game without moves must remain at its initial position");
      }
      return;
    }
    if (!moveHistory.getLast().resultingPosition().equals(currentPosition)) {
      throw new IllegalArgumentException("The last ply must produce the current position");
    }
  }

  private String displayTitle() {
    String white = whitePlayer.map(GamePlayer::getName).orElse("?");
    String black = blackPlayer.map(GamePlayer::getName).orElse("?");
    return white + " vs. " + black;
  }

  private void validateClocks() {
    boolean timed = timeControl.flatMap(TimeControl::initialTime).isPresent();
    if (currentClock.timed() != timed
        || clocksBeforeMoves.stream().anyMatch(clock -> clock.timed() != timed)) {
      throw new IllegalArgumentException("Clock snapshots must match the configured time control");
    }
  }

  private void validateTerminalState() {
    Optional<GameResult> terminal = terminalResult(currentPosition);
    if (terminal.isPresent() && terminal.get() != result) {
      throw new IllegalArgumentException("The game result must match its current position");
    }
    if ((result == null) != (terminationReason == null)) {
      throw new IllegalArgumentException("The game result and termination reason must coexist");
    }
    Optional<GameTerminationReason> boardReason = terminalReason(currentPosition);
    if (boardReason.isPresent() && boardReason.get() != terminationReason) {
      throw new IllegalArgumentException("The termination reason must match the terminal position");
    }
  }

  private void validateAcceptedResult(MoveExecutionResult execution) {
    PositionSnapshot position = execution.newSnapshot();
    if (execution.check() != position.check()
        || execution.mate() != position.mate()
        || execution.stalemate() != position.stalemate()) {
      throw new IllegalStateException("Move result flags must match its resulting position");
    }
  }

  private Optional<GameResult> terminalResult(PositionSnapshot position) {
    if (position.mate()) {
      return Optional.of(
          position.activeColor() == PieceColor.WHITE
              ? GameResult.BLACK_WINS
              : GameResult.WHITE_WINS);
    }
    if (position.stalemate()) {
      return Optional.of(GameResult.DRAW);
    }
    return Optional.empty();
  }

  private Optional<GameTerminationReason> terminalReason(PositionSnapshot position) {
    if (position.mate()) {
      return Optional.of(GameTerminationReason.CHECKMATE);
    }
    if (position.stalemate()) {
      return Optional.of(GameTerminationReason.STALEMATE);
    }
    return Optional.empty();
  }

  private void requireGameInProgress() {
    if (result != null) {
      throw new IllegalStateException("The game has already finished");
    }
  }

  private GameResult winFor(PieceColor winner) {
    return winner == PieceColor.WHITE ? GameResult.WHITE_WINS : GameResult.BLACK_WINS;
  }
}
