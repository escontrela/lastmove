package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import java.util.Optional;

/**
 * Read model of the current game state in a session.
 *
 * <p>It is derived from the current {@link PositionSnapshot}; the session does not keep a second,
 * independently mutable copy of these values.
 */
public record GameSessionState(
    PieceColor whoseTurn,
    CastlingRights castlingRights,
    Optional<Square> enPassantTarget,
    int halfmoveClock,
    int fullmoveNumber,
    boolean check,
    boolean mate,
    boolean stalemate,
    Optional<GameResult> result) {

  public GameSessionState {
    Objects.requireNonNull(whoseTurn, "whoseTurn must not be null");
    Objects.requireNonNull(castlingRights, "castlingRights must not be null");
    enPassantTarget = Objects.requireNonNull(enPassantTarget, "enPassantTarget must not be null");
    if (halfmoveClock < 0) {
      throw new IllegalArgumentException("halfmoveClock must not be negative");
    }
    if (fullmoveNumber < 1) {
      throw new IllegalArgumentException("fullmoveNumber must be at least one");
    }
    result = Objects.requireNonNull(result, "result must not be null");
  }
}
