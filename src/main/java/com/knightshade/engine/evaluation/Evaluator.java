package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Position;

/**
 * Scores a position from White's point of view.
 *
 * <p>Implementations must be stateless. The search passes a read-only {@link Position} so the
 * evaluator never depends on a specific board representation.
 */
public interface Evaluator {

  /** Returns a centipawn score; positive values favour White, negative favour Black. */
  int evaluate(Position position);
}
