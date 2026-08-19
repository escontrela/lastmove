package com.knightshade.engine.search;

import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.ordering.MoveOrderer;
import com.knightshade.engine.see.See;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.List;
import java.util.Objects;

/**
 * Captures-only search that extends the horizon to avoid the "horizon effect" on tactical
 * exchanges.
 *
 * <p>When the side to move is in check, every legal evasion is searched instead, so the stand-pat
 * score is never accepted in a position where a capture would not resolve the check. Losing
 * captures (negative SEE) are skipped when not in check.
 */
public final class QuiescenceSearch {

  private final MoveGenerator moveGenerator;
  private final Evaluator evaluator;
  private final MoveOrderer moveOrderer;
  private long nodes;

  public QuiescenceSearch(
      MoveGenerator moveGenerator, Evaluator evaluator, MoveOrderer moveOrderer) {
    this.moveGenerator = Objects.requireNonNull(moveGenerator, "moveGenerator must not be null");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    this.moveOrderer = Objects.requireNonNull(moveOrderer, "moveOrderer must not be null");
  }

  void resetNodes() {
    nodes = 0;
  }

  long nodes() {
    return nodes;
  }

  public int search(Board board, int alpha, int beta, int ply, StopSignal stop) {
    nodes++;
    if (stop.shouldStop()) {
      return alpha;
    }
    if (ply >= Scores.MAX_PLY) {
      return evaluateFromSideToMove(board);
    }

    if (board.inCheck(board.sideToMove())) {
      List<Move> evasions = moveGenerator.generate(board);
      if (evasions.isEmpty()) {
        return -(Scores.MATE - ply);
      }
      for (Move move : evasions) {
        board.make(move);
        int score = -search(board, -beta, -alpha, ply + 1, stop);
        board.unmake();
        if (score >= beta) {
          return beta;
        }
        if (score > alpha) {
          alpha = score;
        }
      }
      return alpha;
    }

    int standPat = evaluateFromSideToMove(board);
    if (standPat >= beta) {
      return beta;
    }
    if (standPat > alpha) {
      alpha = standPat;
    }
    List<Move> captures = moveOrderer.orderCaptures(board, moveGenerator.generateCaptures(board));
    for (Move move : captures) {
      if (See.ge(board, move, 0)) {
        board.make(move);
        int score = -search(board, -beta, -alpha, ply + 1, stop);
        board.unmake();
        if (score >= beta) {
          return beta;
        }
        if (score > alpha) {
          alpha = score;
        }
      }
    }
    return alpha;
  }

  private int evaluateFromSideToMove(Board board) {
    int score = evaluator.evaluate(board);
    return board.sideToMove() == PieceColor.WHITE ? score : -score;
  }
}
