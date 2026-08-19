package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Position;

/**
 * One positional evaluation feature, scored from White's point of view in centipawns.
 *
 * <p>Terms are composable and stateless so the whole evaluation can be assembled from small,
 * independently testable pieces.
 */
@FunctionalInterface
public interface PositionalTerm {

  int evaluate(Position position);
}
