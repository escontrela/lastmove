package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.evaluation.PieceValues;
import com.knightshade.engine.see.See;
import java.util.ArrayList;
import java.util.function.ToIntFunction;
import java.util.List;

/**
 * Orders moves by descending priority: transposition move, sound captures/promotions (MVV-LVA),
 * killer moves, history-scored quiet moves, then losing captures.
 */
public final class MvvLvaMoveOrderer implements MoveOrderer {

  private static final int TRANSPOSITION_SCORE = 1_000_000;
  private static final int CAPTURE_BASE = 100_000;
  private static final int PRIMARY_KILLER_SCORE = 50_000;
  private static final int SECONDARY_KILLER_SCORE = 40_000;
  private static final int LOSING_CAPTURE_PENALTY = 200_000;

  @Override
  public List<Move> order(Board board, List<Move> moves, OrderingContext context) {
    if (moves.size() <= 1) {
      return moves;
    }
    return orderOnce(moves, move -> score(board, move, context));
  }

  @Override
  public List<Move> orderCaptures(Board board, List<Move> captures) {
    return orderOnce(captures, move -> captureScore(board, move));
  }

  // Small move lists benefit from stable insertion sort. In particular SEE is evaluated once
  // per move, rather than repeatedly inside a comparison function.
  private List<Move> orderOnce(List<Move> moves, ToIntFunction<Move> scorer) {
    if (moves.size() <= 1) {
      return moves;
    }
    List<Move> ordered = new ArrayList<>(moves);
    int[] scores = new int[moves.size()];
    for (int i = 0; i < moves.size(); i++) {
      Move move = moves.get(i);
      int value = scorer.applyAsInt(move);
      int j = i;
      while (j > 0 && scores[j - 1] < value) {
        ordered.set(j, ordered.get(j - 1));
        scores[j] = scores[j - 1];
        j--;
      }
      ordered.set(j, move);
      scores[j] = value;
    }
    return ordered;
  }

  private int score(Board board, Move move, OrderingContext context) {
    if (move.equals(context.transpositionMove())) {
      return TRANSPOSITION_SCORE;
    }
    if (move.isCapture() || move.isPromotion()) {
      return CAPTURE_BASE + captureScore(board, move);
    }
    if (move.equals(context.killers().primary(context.ply()))) {
      return PRIMARY_KILLER_SCORE;
    }
    if (move.equals(context.killers().secondary(context.ply()))) {
      return SECONDARY_KILLER_SCORE;
    }
    return Math.min(SECONDARY_KILLER_SCORE - 1, context.history().get(board.sideToMove(), move));
  }

  private int captureScore(Board board, Move move) {
    int victim = move.isCapture() ? PieceValues.of(move.captured()) : 0;
    int attacker = PieceValues.of(Piece.type(board.pieceAt(move.from())));
    int promotion = move.isPromotion() ? PieceValues.of(move.promotion()) : 0;
    int base = victim * 10 - attacker + promotion;
    if (!move.isPromotion() && victim < attacker && See.evaluate(board, move) < 0) {
      base -= LOSING_CAPTURE_PENALTY;
    }
    return base;
  }
}
