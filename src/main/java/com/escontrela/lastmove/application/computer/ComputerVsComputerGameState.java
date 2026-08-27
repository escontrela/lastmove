package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.*;
import java.util.List;
import java.util.Optional;

/** Snapshot of a transient engine match. It is deliberately never persisted. */
public record ComputerVsComputerGameState(
    GameId gameId, GamePlayer whitePlayer, GamePlayer blackPlayer,
    ComputerEngineDescriptor whiteEngine, ComputerEngineDescriptor blackEngine,
    PositionSnapshot initialPosition, PositionSnapshot position, List<Ply> moves,
    GameClockSnapshot clock, ComputerGamePhase phase, Optional<GameResult> result,
    Optional<GameTerminationReason> terminationReason, boolean stopped, Optional<String> message) {}
