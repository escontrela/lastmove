package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable read model of the current rules state of a progressive chess game.
 *
 * <p>It is derived from the authoritative {@link PositionSnapshot}; it never represents a second
 * independently mutable copy of the board state.
 */
public record GameStateSnapshot(
    PieceColor whoseTurn,
    CastlingRights castlingRights,
    Optional<Square> enPassantTarget,
    int halfmoveClock,
    int fullmoveNumber,
    boolean check,
    boolean mate,
    boolean stalemate,
    Optional<GameResult> result) {

  public GameStateSnapshot {
    Objects.requireNonNull(whoseTurn, "whoseTurn must not be null");
    Objects.requireNonNull(castlingRights, "castlingRights must not be null");
    enPassantTarget = Objects.requireNonNull(enPassantTarget, "enPassantTarget must not be null");
    result = Objects.requireNonNull(result, "result must not be null");
    if (halfmoveClock < 0) {
      throw new IllegalArgumentException("halfmoveClock must not be negative");
    }
    if (fullmoveNumber < 1) {
      throw new IllegalArgumentException("fullmoveNumber must be at least one");
    }
    if ((mate || stalemate) && result.isEmpty()) {
      throw new IllegalArgumentException("a terminal board position requires a game result");
    }
  }
}
