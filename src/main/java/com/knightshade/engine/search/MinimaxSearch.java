package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.escontrela.lastmove.domain.common.PieceColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * v0 search: fixed-depth negamax (minimax) with a material evaluator.
 *
 * <p>Alpha-beta pruning, quiescence and move-ordering heuristics are added in later versions. The
 * structure is intentionally plain so those optimizations slot in without changing the {@link
 * Search} contract.
 */
public final class MinimaxSearch implements Search {

  private static final int MATE = 1_000_000;
  private static final int DEFAULT_DEPTH = 3;

  private final MoveGenerator moveGenerator;
  private final Evaluator evaluator;
  private long nodes;

  public MinimaxSearch(MoveGenerator moveGenerator, Evaluator evaluator) {
    this.moveGenerator = Objects.requireNonNull(moveGenerator, "moveGenerator must not be null");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop) {
    Objects.requireNonNull(board, "board must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");

    nodes = 0;
    long startedAt = System.nanoTime();
    int depth = limits.maxDepth() > 0 ? limits.maxDepth() : DEFAULT_DEPTH;

    List<Move> rootMoves = orderMoves(moveGenerator.generate(board));
    if (rootMoves.isEmpty()) {
      return new SearchResult(null, terminalScore(board), depth, nodes, elapsedMillis(startedAt));
    }

    int bestScore = Integer.MIN_VALUE;
    Move bestMove = rootMoves.getFirst();
    for (Move move : rootMoves) {
      if (stop.shouldStop()) {
        break;
      }
      board.make(move);
      int score = -negamax(board, depth - 1, 1, stop);
      board.unmake();
      if (score > bestScore) {
        bestScore = score;
        bestMove = move;
      }
    }
    return new SearchResult(bestMove, bestScore, depth, nodes, elapsedMillis(startedAt));
  }

  private int negamax(Board board, int depth, int ply, StopSignal stop) {
    nodes++;
    if (stop.shouldStop()) {
      return 0;
    }
    List<Move> legal = orderMoves(moveGenerator.generate(board));
    if (legal.isEmpty()) {
      return board.inCheck(board.sideToMove()) ? -(MATE - ply) : 0;
    }
    if (depth <= 0) {
      return evaluateFromSideToMove(board);
    }
    int best = Integer.MIN_VALUE;
    for (Move move : legal) {
      board.make(move);
      int score = -negamax(board, depth - 1, ply + 1, stop);
      board.unmake();
      if (score > best) {
        best = score;
      }
    }
    return best;
  }

  private int evaluateFromSideToMove(Board board) {
    int score = evaluator.evaluate(board);
    return board.sideToMove() == PieceColor.WHITE ? score : -score;
  }

  private int terminalScore(Board board) {
    return board.inCheck(board.sideToMove()) ? -MATE : 0;
  }

  private List<Move> orderMoves(List<Move> moves) {
    if (moves.size() <= 1) {
      return moves;
    }
    List<Move> ordered = new ArrayList<>(moves);
    ordered.sort(Comparator.comparingInt(move -> move.isCapture() ? 0 : 1));
    return ordered;
  }

  private long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
