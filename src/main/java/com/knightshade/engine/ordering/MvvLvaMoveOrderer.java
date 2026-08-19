package com.knightshade.engine.ordering;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.evaluation.PieceValues;
import com.knightshade.engine.see.See;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orders moves by descending priority: transposition move, captures/promotions (MVV-LVA), killer
 * moves, then history-scored quiet moves.
 */
public final class MvvLvaMoveOrderer implements MoveOrderer {

  private static final int TRANSPOSITION_SCORE = 1_000_000;
  private static final int CAPTURE_BASE = 100_000;
  private static final int PRIMARY_KILLER_SCORE = 50_000;
  private static final int SECONDARY_KILLER_SCORE = 40_000;
  private static final int LOSING_CAPTURE_PENALTY = 90_000;

  @Override
  public List<Move> order(Board board, List<Move> moves, OrderingContext context) {
    if (moves.size() <= 1) {
      return moves;
    }
    List<Move> ordered = new ArrayList<>(moves);
    ordered.sort(
        Comparator.comparingInt((Move move) -> -score(board, move, context)));
    return ordered;
  }

  @Override
  public List<Move> orderCaptures(Board board, List<Move> captures) {
    if (captures.size() <= 1) {
      return captures;
    }
    List<Move> ordered = new ArrayList<>(captures);
    ordered.sort(
        Comparator.comparingInt((Move move) -> -captureScore(board, move)));
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
    return Math.min(SECONDARY_KILLER_SCORE - 1, context.history().get(move));
  }

  private int captureScore(Board board, Move move) {
    int victim = move.isCapture() ? PieceValues.of(move.captured()) : 0;
    int attacker = PieceValues.of(Piece.type(board.pieceAt(move.from())));
    int promotion = move.isPromotion() ? PieceValues.of(move.promotion()) : 0;
    int base = victim * 10 - attacker + promotion;
    if (victim < attacker && See.evaluate(board, move) < 0) {
      base -= LOSING_CAPTURE_PENALTY;
    }
    return base;
  }
}
