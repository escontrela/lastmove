package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameClockSnapshot;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.GameTerminationReason;
import com.escontrela.lastmove.domain.game.GamePlayer;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.game.TimeControl;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable application DTO rendered by a progressive-game screen. */
public record ComputerGameState(
    GameId gameId,
    GamePlayer whitePlayer,
    GamePlayer blackPlayer,
    PieceColor humanColor,
    ComputerEngineDescriptor engine,
    PositionSnapshot initialPosition,
    PositionSnapshot position,
    List<Ply> moves,
    GameStateSnapshot gameState,
    GameClockSnapshot clock,
    Optional<TimeControl> timeControl,
    ComputerGamePhase phase,
    Optional<GameResult> result,
    Optional<GameTerminationReason> terminationReason,
    boolean canMove,
    boolean canTakeBack,
    OpeningPracticeState openingPracticeState,
    Optional<String> message) {

  public ComputerGameState {
    Objects.requireNonNull(gameId, "gameId must not be null");
    Objects.requireNonNull(whitePlayer, "whitePlayer must not be null");
    Objects.requireNonNull(blackPlayer, "blackPlayer must not be null");
    Objects.requireNonNull(humanColor, "humanColor must not be null");
    Objects.requireNonNull(engine, "engine must not be null");
    Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    Objects.requireNonNull(position, "position must not be null");
    moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
    Objects.requireNonNull(gameState, "gameState must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    timeControl = Objects.requireNonNull(timeControl, "timeControl must not be null");
    Objects.requireNonNull(phase, "phase must not be null");
    Objects.requireNonNull(openingPracticeState, "openingPracticeState must not be null");
    result = Objects.requireNonNull(result, "result must not be null");
    terminationReason =
        Objects.requireNonNull(terminationReason, "terminationReason must not be null");
    message = Objects.requireNonNull(message, "message must not be null");
  }
}
