package com.knightshade.engine.search;

import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.ordering.MoveOrderer;
import com.knightshade.engine.see.See;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.util.List;
import java.util.Objects;

/**
 * Captures-only search that extends the horizon to avoid the "horizon effect" on tactical
 * exchanges.
 *
 * <p>When the side to move is in check, every legal evasion is searched instead, so the stand-pat
 * score is never accepted in a position where a capture would not resolve the check. Losing
 * captures (negative SEE) are normally skipped when not in check; promotions, checking captures
 * and captures of major pieces are retained because pruning them is tactically risky.
 */
public final class QuiescenceSearch {

  private static final int CHECK_EXTENSION_NODE_LIMIT = 8_000;

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
    return searchInternal(board, alpha, beta, ply, stop, false);
  }

  /**
   * Entry point used by the main search at the horizon: also searches quiet checking moves so a
   * forcing check at the leaf is not hidden by the captures-only filter.
   */
  int searchWithQuietChecks(Board board, int alpha, int beta, int ply, StopSignal stop) {
    return searchInternal(board, alpha, beta, ply, stop, true);
  }

  private int searchInternal(
      Board board, int alpha, int beta, int ply, StopSignal stop, boolean quietChecks) {
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
        int score = -searchInternal(board, -beta, -alpha, ply + 1, stop, false);
        board.unmake();
        if (stop.shouldStop()) {
          return alpha;
        }
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
      boolean searchCapture = shouldSearchCapture(board, move);
      if (searchCapture) {
        board.make(move);
        int score = -searchInternal(board, -beta, -alpha, ply + 1, stop, false);
        board.unmake();
        if (stop.shouldStop()) {
          return alpha;
        }
        if (score >= beta) {
          return beta;
        }
        if (score > alpha) {
          alpha = score;
        }
      }
    }
    if (quietChecks && nodes <= CHECK_EXTENSION_NODE_LIMIT) {
      alpha = searchQuietChecks(board, alpha, beta, ply, stop);
    }
    return alpha;
  }

  /**
   * SEE pruning is useful for ordinary losing exchanges, but it must never hide a promotion, a
   * checking capture or the capture of a major piece. Those moves are disproportionately tactical
   * and searching them is cheap insurance against horizon blunders involving queens and rooks.
   */
  private boolean shouldSearchCapture(Board board, Move move) {
    if (move.isPromotion()
        || move.captured() == PieceType.QUEEN
        || move.captured() == PieceType.ROOK
        || See.ge(board, move, 0)) {
      return true;
    }
    board.make(move);
    boolean givesCheck = board.inCheck(board.sideToMove());
    board.unmake();
    return givesCheck;
  }

  private int searchQuietChecks(Board board, int alpha, int beta, int ply, StopSignal stop) {
    List<Move> legal = moveGenerator.generate(board);
    for (Move move : legal) {
      if (move.isCapture() || move.isPromotion()) {
        continue;
      }
      board.make(move);
      boolean givesCheck = board.inCheck(board.sideToMove());
      if (!givesCheck) {
        board.unmake();
        continue;
      }
      int score = -searchInternal(board, -beta, -alpha, ply + 1, stop, false);
      board.unmake();
      if (stop.shouldStop()) {
        return alpha;
      }
      if (score >= beta) {
        return beta;
      }
      if (score > alpha) {
        alpha = score;
      }
    }
    return alpha;
  }

  private int evaluateFromSideToMove(Board board) {
    int score = evaluator.evaluate(board);
    return board.sideToMove() == PieceColor.WHITE ? score : -score;
  }
}
