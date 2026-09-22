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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final MoveGenerator moveGenerator;
  private final Evaluator evaluator;
  private final MoveOrderer moveOrderer;
  private long nodes;
  private SearchStats telemetry;

  public QuiescenceSearch(
      MoveGenerator moveGenerator, Evaluator evaluator, MoveOrderer moveOrderer) {
    this.moveGenerator = Objects.requireNonNull(moveGenerator, "moveGenerator must not be null");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    this.moveOrderer = Objects.requireNonNull(moveOrderer, "moveOrderer must not be null");
  }

  void resetNodes() {
    nodes = 0;
  }

  void setTelemetry(SearchStats telemetry) {
    this.telemetry = telemetry;
  }

  long nodes() {
    return nodes;
  }

  public int search(Board board, int alpha, int beta, int ply, StopSignal stop) {
    return searchInternal(board, alpha, beta, ply, stop, false, new HashMap<>());
  }

  /**
   * Entry point used by the main search at the horizon: also searches quiet checking moves so a
   * forcing check at the leaf is not hidden by the captures-only filter.
   */
  int searchWithQuietChecks(Board board, int alpha, int beta, int ply, StopSignal stop) {
    return searchWithQuietChecks(board, alpha, beta, ply, stop, new HashMap<>());
  }

  int searchWithQuietChecks(
      Board board, int alpha, int beta, int ply, StopSignal stop, Map<Long, Integer> repetitions) {
    return searchInternal(board, alpha, beta, ply, stop, true, repetitions);
  }

  private int searchInternal(
      Board board, int alpha, int beta, int ply, StopSignal stop, boolean quietChecks,
      Map<Long, Integer> repetitions) {
    nodes++;
    if (telemetry != null) telemetry.quiescenceEntries++;
    if (stop.shouldStop()) {
      return alpha;
    }
    if (ply >= Scores.MAX_PLY) {
      return evaluateFromSideToMove(board);
    }

    boolean inCheck = board.inCheck(board.sideToMove());
    if (repetitions != null && (board.halfmoveClock() >= 100
        || repetitions.getOrDefault(board.zobristKey(), 0) >= 3)) {
      return inCheck && !hasLegalMove(board) ? -(Scores.MATE - ply) : 0;
    }
    if (inCheck) {
      if (telemetry != null) telemetry.moveListsGenerated++;
      List<Move> evasions = moveGenerator.generate(board);
      if (evasions.isEmpty()) {
        return -(Scores.MATE - ply);
      }
      for (Move move : evasions) {
        int score = searchChild(board, move, alpha, beta, ply, stop, repetitions);
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

    // A static evaluation is sufficient for the overwhelmingly common fail-high path.  Check
    // for stalemate only after it fails high: this preserves a draw score without allocating a
    // full move list for positions that are clearly good enough.
    int standPat = evaluateFromSideToMove(board);
    if (standPat >= beta) {
      if (telemetry != null) telemetry.standPatCutoffs++;
      return hasLegalMove(board) ? beta : 0;
    }
    if (standPat > alpha) {
      alpha = standPat;
    }

    // Materialize moves only when the static evaluation cannot cut.  Quiet-check mode needs the
    // complete list so it can retain the existing capture/promotion/check set and ordering.
    if (telemetry != null) telemetry.moveListsGenerated++;
    List<Move> legal = quietChecks ? moveGenerator.generate(board) : null;
    List<Move> captures = quietChecks
        ? legal.stream().filter(move -> move.isCapture() || move.isPromotion()).toList()
        : moveGenerator.generateCaptures(board);
    if (quietChecks ? legal.isEmpty() : captures.isEmpty() && !hasLegalMove(board)) {
      return 0;
    }
    captures = moveOrderer.orderCaptures(board, captures);
    for (Move move : captures) {
      boolean searchCapture = shouldSearchCapture(board, move);
      if (searchCapture) {
        int score = searchChild(board, move, alpha, beta, ply, stop, repetitions);
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
    if (quietChecks) {
      alpha = searchQuietChecks(board, legal, alpha, beta, ply, stop, repetitions);
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
        || seeAtLeastZero(board, move)) {
      return true;
    }
    if (telemetry != null) telemetry.seePrunes++;
    board.make(move);
    boolean givesCheck = board.inCheck(board.sideToMove());
    board.unmake();
    return givesCheck;
  }

  private int searchQuietChecks(Board board, List<Move> legal, int alpha, int beta, int ply,
      StopSignal stop, Map<Long, Integer> repetitions) {
    for (Move move : legal) {
      if (move.isCapture() || move.isPromotion()) {
        continue;
      }
      if (telemetry != null) telemetry.quietChecksExamined++;
      board.make(move);
      boolean givesCheck = board.inCheck(board.sideToMove());
      if (!givesCheck) {
        board.unmake();
        continue;
      }
      board.unmake();
      int score = searchChild(board, move, alpha, beta, ply, stop, repetitions);
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

  private int searchChild(Board board, Move move, int alpha, int beta, int ply,
      StopSignal stop, Map<Long, Integer> repetitions) {
    board.make(move);
    long key = board.zobristKey();
    if (repetitions != null) {
      repetitions.merge(key, 1, Integer::sum);
    }
    try {
      return -searchInternal(board, -beta, -alpha, ply + 1, stop, false, repetitions);
    } finally {
      if (repetitions != null) {
        repetitions.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
      }
      board.unmake();
    }
  }

  private int evaluateFromSideToMove(Board board) {
    int score = evaluator.evaluate(board);
    return board.sideToMove() == PieceColor.WHITE ? score : -score;
  }

  private boolean hasLegalMove(Board board) {
    if (telemetry != null) telemetry.stalemateChecks++;
    return moveGenerator.hasLegalMove(board);
  }

  private boolean seeAtLeastZero(Board board, Move move) {
    if (telemetry != null) telemetry.seeEvaluations++;
    return See.ge(board, move, 0);
  }
}
