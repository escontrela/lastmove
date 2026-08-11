package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoMoveValidator;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application service that validates moves against a supplied position.
 *
 * <p>The caller owns the {@code GameSession}: this service neither resolves session identifiers
 * nor retains mutable game state.
 */
@Service
public class GameMoveService {

  private final ChesspressoMoveValidator moveValidator;

  public GameMoveService(ChesspressoMoveValidator moveValidator) {
    this.moveValidator = Objects.requireNonNull(moveValidator, "moveValidator must not be null");
  }

  /** Returns a complete snapshot for a new game using the standard initial position. */
  public PositionSnapshot startingPosition() {
    return moveValidator.startingPosition();
  }

  /** Converts a valid FEN position into the complete snapshot used by a new session. */
  public PositionSnapshot snapshotFor(Fen fen) {
    return moveValidator.snapshotFor(fen);
  }

  /** Validates one requested move without changing the supplied position or any session. */
  public MoveExecutionResult validate(PositionSnapshot currentPosition, MoveCommand command) {
    return moveValidator.validate(currentPosition, command);
  }
}
