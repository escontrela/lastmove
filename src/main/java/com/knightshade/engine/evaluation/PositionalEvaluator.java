package com.knightshade.engine.evaluation;

import com.knightshade.engine.board.Position;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.evaluation.term.BishopPairTerm;
import com.knightshade.engine.evaluation.term.CenterControlTerm;
import com.knightshade.engine.evaluation.term.DevelopmentTerm;
import com.knightshade.engine.evaluation.term.KingSafetyTerm;
import com.knightshade.engine.evaluation.term.MaterialTerm;
import com.knightshade.engine.evaluation.term.MobilityTerm;
import com.knightshade.engine.evaluation.term.PassedPawnTerm;
import com.knightshade.engine.evaluation.term.PawnStructureTerm;
import java.util.List;

/**
 * v3.5 evaluator: a composition of positional terms, White minus Black.
 *
 * <p>Material + piece-square tables form the base; mobility, king safety, development, center
 * control, pawn structure, passed pawns and the bishop pair are added on top. Each term is an
 * independent {@link PositionalTerm} so features can be tested and tuned in isolation.
 */
public final class PositionalEvaluator implements Evaluator {

  // Exact static-score cache. Draw clocks/history are handled by search, not these terms.
  // Like the search workspace, this evaluator instance belongs to one search thread.
  private static final int CACHE_SIZE = 1 << 15;
  private final long[] cacheKeys = new long[CACHE_SIZE];
  private final int[] cacheScores = new int[CACHE_SIZE];
  private final boolean[] cacheUsed = new boolean[CACHE_SIZE];

  private final List<PositionalTerm> terms =
      List.of(
          new MaterialTerm(),
          new MobilityTerm(),
          new KingSafetyTerm(),
          new DevelopmentTerm(),
          new CenterControlTerm(),
          new PawnStructureTerm(),
          new PassedPawnTerm(),
          new BishopPairTerm());

  @Override
  public int evaluate(Position position) {
    if (position instanceof Board board) {
      long key = board.zobristKey();
      int slot = (int) key & (CACHE_SIZE - 1);
      if (cacheUsed[slot] && cacheKeys[slot] == key) {
        return cacheScores[slot];
      }
      int score = evaluateTerms(position);
      cacheKeys[slot] = key;
      cacheScores[slot] = score;
      cacheUsed[slot] = true;
      return score;
    }
    return evaluateTerms(position);
  }

  private int evaluateTerms(Position position) {
    int score = 0;
    for (PositionalTerm term : terms) {
      score += term.evaluate(position);
    }
    return score;
  }
}
