package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.ordering.HistoryTable;
import com.knightshade.engine.ordering.KillerMoves;
import com.knightshade.engine.ordering.MoveOrderer;
import com.knightshade.engine.ordering.MvvLvaMoveOrderer;
import com.knightshade.engine.ordering.OrderingContext;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.List;
import java.util.Objects;

/**
 * v1 search: negamax with alpha-beta pruning and quiescence search.
 *
 * <p>Move ordering uses MVV-LVA for captures and killer moves for quiet refutations. This search is
 * superseded by {@link IterativeDeepeningSearch} in v2 but remains a self-contained, tested step.
 */
public final class AlphaBetaSearch implements Search {

  private static final int DEFAULT_DEPTH = 4;

  private final MoveGenerator moveGenerator;
  private final Evaluator evaluator;
  private final MoveOrderer moveOrderer;
  private final QuiescenceSearch quiescence;
  private long nodes;

  public AlphaBetaSearch(MoveGenerator moveGenerator, Evaluator evaluator) {
    this.moveGenerator = Objects.requireNonNull(moveGenerator, "moveGenerator must not be null");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    this.moveOrderer = new MvvLvaMoveOrderer();
    this.quiescence = new QuiescenceSearch(moveGenerator, evaluator, moveOrderer);
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop) {
    Objects.requireNonNull(board, "board must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");

    nodes = 0;
    quiescence.resetNodes();
    long startedAt = System.nanoTime();
    int depth = limits.maxDepth() > 0 ? limits.maxDepth() : DEFAULT_DEPTH;

    KillerMoves killers = new KillerMoves();
    HistoryTable history = new HistoryTable();
    List<Move> rootMoves =
        moveOrderer.order(
            board, moveGenerator.generate(board), new OrderingContext(0, killers, history, null));
    if (rootMoves.isEmpty()) {
      return new SearchResult(
          null, terminalScore(board), depth, totalNodes(), elapsedMillis(startedAt));
    }

    int alpha = -Scores.INF;
    Move bestMove = rootMoves.getFirst();
    for (Move move : rootMoves) {
      if (stop.shouldStop()) {
        break;
      }
      board.make(move);
      int score = -negamax(board, depth - 1, -Scores.INF, -alpha, 1, killers, history, stop);
      board.unmake();
      if (score > alpha) {
        alpha = score;
        bestMove = move;
      }
    }
    return new SearchResult(bestMove, alpha, depth, totalNodes(), elapsedMillis(startedAt));
  }

  private int negamax(
      Board board,
      int depth,
      int alpha,
      int beta,
      int ply,
      KillerMoves killers,
      HistoryTable history,
      StopSignal stop) {
    nodes++;
    if (stop.shouldStop()) {
      return alpha;
    }
    if (ply >= Scores.MAX_PLY) {
      return evaluateFromSideToMove(board);
    }
    List<Move> legal =
        moveOrderer.order(
            board,
            moveGenerator.generate(board),
            new OrderingContext(ply, killers, history, null));
    if (legal.isEmpty()) {
      return board.inCheck(board.sideToMove()) ? -(Scores.MATE - ply) : 0;
    }
    if (depth <= 0) {
      return quiescence.search(board, alpha, beta, ply, stop);
    }
    for (Move move : legal) {
      board.make(move);
      int score = -negamax(board, depth - 1, -beta, -alpha, ply + 1, killers, history, stop);
      board.unmake();
      if (score >= beta) {
        if (!move.isCapture() && !move.isPromotion()) {
          killers.record(move, ply);
        }
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

  private int terminalScore(Board board) {
    return board.inCheck(board.sideToMove()) ? -Scores.MATE : 0;
  }

  private long totalNodes() {
    return nodes + quiescence.nodes();
  }

  private long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
