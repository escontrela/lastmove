package com.escontrela.lastmove.application.dto;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveCommand;
import java.util.Objects;
import java.util.Optional;

/** Immutable command requesting one move in the current position of a session. */
public record MoveRequest(
    SessionId sessionId,
    Square fromSquare,
    Square toSquare,
    Optional<PieceType> promotionPiece) {

  public MoveRequest {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(fromSquare, "fromSquare must not be null");
    Objects.requireNonNull(toSquare, "toSquare must not be null");
    promotionPiece = Objects.requireNonNull(promotionPiece, "promotionPiece must not be null");
  }

  public MoveCommand toMoveCommand() {
    return new MoveCommand(fromSquare, toSquare, promotionPiece);
  }
}
