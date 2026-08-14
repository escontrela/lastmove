package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;

/**
 * Domain contract for reconstructing positions and executing chess moves.
 *
 * <p>The contract keeps game aggregates and application workflows independent from the rules
 * implementation. An infrastructure adapter may currently delegate to Chesspresso and can later
 * be replaced by a native rules engine without changing its callers. Implementations must be
 * stateless: the supplied {@link PositionSnapshot} is the complete input state for each move.
 */
public interface ChessRulesEngine {

  /** Returns a complete snapshot of the standard initial chess position. */
  PositionSnapshot startingPosition();

  /** Reconstructs a complete, engine-neutral position snapshot from a valid FEN. */
  PositionSnapshot positionFrom(Fen fen);

  /**
   * Validates and executes a move against the supplied position without mutating that snapshot.
   *
   * @return an accepted result containing the new position, or a rejected result that preserves
   *     the supplied position
   */
  MoveExecutionResult execute(PositionSnapshot currentPosition, MoveCommand command);

  /**
   * Resolves and executes a SAN move in the context of the supplied position.
   *
   * <p>SAN is position-dependent: implementations must resolve ambiguity, castling, captures,
   * promotion and check suffixes against the legal moves of {@code currentPosition}.
   *
   * @return an accepted result containing the new position, or a rejected result that preserves
   *     the supplied position
   */
  MoveExecutionResult execute(PositionSnapshot currentPosition, SanMove move);
}
