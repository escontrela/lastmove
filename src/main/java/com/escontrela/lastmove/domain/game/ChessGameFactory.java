package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.notation.Fen;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain factory that creates or rehydrates {@link ChessGame} aggregates with a rules engine.
 *
 * <p>The engine is deliberately injected at construction time and is not part of persisted game
 * data. This makes replacing Chesspresso or rehydrating a stored game transparent to the aggregate.
 */
public final class ChessGameFactory {

  private final ChessRulesEngine rulesEngine;

  public ChessGameFactory(ChessRulesEngine rulesEngine) {
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
  }

  /** Creates a progressive game at the standard initial position. */
  public ChessGame createInitial(
      Player whitePlayer, Player blackPlayer, Optional<TimeControl> timeControl) {
    return create(
        rulesEngine.startingPosition(),
        Optional.of(Objects.requireNonNull(whitePlayer, "whitePlayer must not be null")),
        Optional.of(Objects.requireNonNull(blackPlayer, "blackPlayer must not be null")),
        timeControl);
  }

  /** Creates a progressive game from the supplied FEN position. */
  public ChessGame createFrom(
      Fen fen, Player whitePlayer, Player blackPlayer, Optional<TimeControl> timeControl) {
    Objects.requireNonNull(fen, "fen must not be null");
    return create(
        rulesEngine.positionFrom(fen),
        Optional.of(Objects.requireNonNull(whitePlayer, "whitePlayer must not be null")),
        Optional.of(Objects.requireNonNull(blackPlayer, "blackPlayer must not be null")),
        timeControl);
  }

  /**
   * Creates a short-lived linear game from an analysis position, without players or clock.
   *
   * <p>This will be used later by an analysis session to validate a continuation through the same
   * aggregate API as a progressive game.
   */
  public ChessGame createAnalysisGame(PositionSnapshot position) {
    return create(
        Objects.requireNonNull(position, "position must not be null"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  /** Creates a short-lived analysis game at the standard initial position. */
  public ChessGame createAnalysisGame() {
    return createAnalysisGame(rulesEngine.startingPosition());
  }

  /** Creates a short-lived analysis game from a FEN position. */
  public ChessGame createAnalysisGame(Fen fen) {
    return createAnalysisGame(
        rulesEngine.positionFrom(Objects.requireNonNull(fen, "fen must not be null")));
  }

  /** Rehydrates a previously stored progressive game and restores its injected rules engine. */
  public ChessGame resume(
      GameId id,
      PositionSnapshot initialPosition,
      PositionSnapshot currentPosition,
      List<Ply> moveHistory,
      List<GameClockSnapshot> clocksBeforeMoves,
      GameClockSnapshot currentClock,
      Optional<Player> whitePlayer,
      Optional<Player> blackPlayer,
      Optional<TimeControl> timeControl,
      Optional<GameResult> result,
      Optional<GameTerminationReason> terminationReason) {
    return new ChessGame(
        id,
        initialPosition,
        currentPosition,
        moveHistory,
        clocksBeforeMoves,
        currentClock,
        whitePlayer,
        blackPlayer,
        timeControl,
        result,
        terminationReason,
        rulesEngine);
  }

  private ChessGame create(
      PositionSnapshot position,
      Optional<Player> whitePlayer,
      Optional<Player> blackPlayer,
      Optional<TimeControl> timeControl) {
    Optional<TimeControl> requiredTimeControl =
        Objects.requireNonNull(timeControl, "timeControl must not be null");
    return new ChessGame(
        GameId.random(),
        position,
        position,
        List.of(),
        List.of(),
        GameClockSnapshot.initial(requiredTimeControl),
        whitePlayer,
        blackPlayer,
        requiredTimeControl,
        terminalResult(position),
        terminalReason(position),
        rulesEngine);
  }

  private Optional<GameResult> terminalResult(PositionSnapshot position) {
    if (position.mate()) {
      return Optional.of(
          position.activeColor() == PieceColor.WHITE
              ? GameResult.BLACK_WINS
              : GameResult.WHITE_WINS);
    }
    return position.stalemate() ? Optional.of(GameResult.DRAW) : Optional.empty();
  }

  private Optional<GameTerminationReason> terminalReason(PositionSnapshot position) {
    if (position.mate()) {
      return Optional.of(GameTerminationReason.CHECKMATE);
    }
    return position.stalemate()
        ? Optional.of(GameTerminationReason.STALEMATE)
        : Optional.empty();
  }
}
