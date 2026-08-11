package com.escontrela.lastmove.domain.game;

/** A stateful chess game that validates moves and publishes engine-neutral position snapshots. */
public interface ChessGameSession {

  MoveExecutionResult tryMove(MoveCommand command);

  PositionSnapshot currentSnapshot();
}
