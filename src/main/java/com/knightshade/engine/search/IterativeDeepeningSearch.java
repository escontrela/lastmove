package com.knightshade.engine.search;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.evaluation.Evaluator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.ordering.HistoryTable;
import com.knightshade.engine.ordering.KillerMoves;
import com.knightshade.engine.ordering.MoveOrderer;
import com.knightshade.engine.ordering.MvvLvaMoveOrderer;
import com.knightshade.engine.ordering.OrderingContext;
import com.knightshade.engine.time.TimeManager;
import com.knightshade.engine.transposition.TranspositionTable;
import com.knightshade.engine.transposition.TranspositionTable.Entry;
import com.knightshade.engine.transposition.TranspositionTable.ScoreType;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.util.List;
import java.util.Objects;

/**
 * v3 search: iterative deepening with principal variation search, aspiration windows, null-move
 * pruning and late move reductions, backed by a transposition table, killer moves and the history
 * heuristic.
 */
public final class IterativeDeepeningSearch implements Search {

  private static final int DEFAULT_MAX_DEPTH = 64;
  private static final int ASPIRATION_DELTA = 25;

  private final MoveGenerator moveGenerator;
  private final Evaluator evaluator;
  private final MoveOrderer moveOrderer;
  private final QuiescenceSearch quiescence;
  private final TranspositionTable transpositionTable;

  private long nodes;
  private Move bestRootMove;

  public IterativeDeepeningSearch(MoveGenerator moveGenerator, Evaluator evaluator) {
    this(moveGenerator, evaluator, new TranspositionTable());
  }

  IterativeDeepeningSearch(
      MoveGenerator moveGenerator, Evaluator evaluator, TranspositionTable transpositionTable) {
    this.moveGenerator = Objects.requireNonNull(moveGenerator, "moveGenerator must not be null");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    this.moveOrderer = new MvvLvaMoveOrderer();
    this.quiescence = new QuiescenceSearch(moveGenerator, evaluator, moveOrderer);
    this.transpositionTable =
        Objects.requireNonNull(transpositionTable, "transpositionTable must not be null");
  }

  @Override
  public SearchResult search(Board board, SearchLimits limits, StopSignal stop) {
    Objects.requireNonNull(board, "board must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");

    nodes = 0;
    quiescence.resetNodes();
    long startedAt = System.nanoTime();
    int maxDepth = limits.maxDepth() > 0 ? limits.maxDepth() : DEFAULT_MAX_DEPTH;

    List<Move> rootMoves = moveGenerator.generate(board);
    if (rootMoves.isEmpty()) {
      return new SearchResult(null, terminalScore(board), 0, 0, elapsedMillis(startedAt));
    }

    KillerMoves killers = new KillerMoves();
    HistoryTable history = new HistoryTable();
    TimeManager timeManager = new TimeManager(limits.maxTimeMillis());

    bestRootMove = rootMoves.getFirst();
    int bestScore = 0;
    int completedDepth = 0;
    for (int depth = 1; depth <= maxDepth; depth++) {
      int score = searchDepth(board, depth, bestScore, killers, history, stop);
      if (stop.shouldStop()) {
        break;
      }
      completedDepth = depth;
      bestScore = score;
      if (Scores.isMate(score) || timeManager.exceeded()) {
        break;
      }
    }
    return new SearchResult(
        bestRootMove, bestScore, completedDepth, totalNodes(), elapsedMillis(startedAt));
  }

  private int searchDepth(
      Board board,
      int depth,
      int previousScore,
      KillerMoves killers,
      HistoryTable history,
      StopSignal stop) {
    int delta = ASPIRATION_DELTA;
    int alpha = depth <= 1 ? -Scores.INF : Math.max(-Scores.INF, previousScore - delta);
    int beta = depth <= 1 ? Scores.INF : Math.min(Scores.INF, previousScore + delta);
    while (true) {
      if (stop.shouldStop()) {
        return previousScore;
      }
      int score = searchRoot(board, depth, alpha, beta, killers, history, stop);
      if (stop.shouldStop()) {
        return previousScore;
      }
      if (score <= alpha) {
        alpha = Math.max(-Scores.INF, alpha - delta);
      } else if (score >= beta) {
        beta = Math.min(Scores.INF, beta + delta);
      } else {
        return score;
      }
      delta *= 2;
    }
  }

  private int searchRoot(
      Board board,
      int depth,
      int alpha,
      int beta,
      KillerMoves killers,
      HistoryTable history,
      StopSignal stop) {
    Move ttMove = transpositionMoveFor(board);
    List<Move> moves =
        moveOrderer.order(
            board,
            moveGenerator.generate(board),
            new OrderingContext(0, killers, history, ttMove));

    int best = -Scores.INF;
    for (Move move : moves) {
      if (stop.shouldStop()) {
        break;
      }
      board.make(move);
      int score = -pvSearch(board, depth - 1, -beta, -alpha, 1, killers, history, stop);
      board.unmake();
      if (score > best) {
        best = score;
        bestRootMove = move;
      }
      if (score > alpha) {
        alpha = score;
      }
    }
    return best;
  }

  private int pvSearch(
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

    long key = board.zobristKey();
    Entry entry = transpositionTable.probe(key);
    Move ttMove = entry == null ? null : entry.move();
    if (entry != null && entry.depth() >= depth && ply > 0) {
      int stored = Scores.fromTable(entry.score(), ply);
      if (entry.type() == ScoreType.EXACT) {
        return stored;
      }
      if (entry.type() == ScoreType.LOWER_BOUND && stored >= beta) {
        return stored;
      }
      if (entry.type() == ScoreType.UPPER_BOUND && stored <= alpha) {
        return stored;
      }
    }

    List<Move> legal =
        moveOrderer.order(
            board,
            moveGenerator.generate(board),
            new OrderingContext(ply, killers, history, ttMove));
    if (legal.isEmpty()) {
      int score = board.inCheck(board.sideToMove()) ? -(Scores.MATE - ply) : 0;
      transpositionTable.store(
          key, null, Scores.MAX_PLY, Scores.toTable(score, ply), ScoreType.EXACT);
      return score;
    }
    if (depth <= 0) {
      int score = quiescence.search(board, alpha, beta, ply, stop);
      transpositionTable.store(key, null, 0, Scores.toTable(score, ply), ScoreType.EXACT);
      return score;
    }

    boolean inCheck = board.inCheck(board.sideToMove());
    if (!inCheck && depth >= 3 && hasNonPawnMaterial(board, board.sideToMove())) {
      int reduction = 2 + depth / 4;
      board.makeNullMove();
      int score = -pvSearch(board, depth - 1 - reduction, -beta, -beta + 1, ply + 1, killers,
          history, stop);
      board.unmakeNullMove();
      if (score >= beta) {
        return score;
      }
    }

    int alphaOriginal = alpha;
    int best = -Scores.INF;
    Move bestMove = null;
    int moveCount = 0;
    for (Move move : legal) {
      moveCount++;
      boolean reduce =
          !inCheck
              && depth >= 3
              && moveCount >= 4
              && !move.isCapture()
              && !move.isPromotion()
              && !move.equals(killers.primary(ply))
              && !move.equals(killers.secondary(ply));

      board.make(move);
      int score;
      if (moveCount == 1) {
        score = -pvSearch(board, depth - 1, -beta, -alpha, ply + 1, killers, history, stop);
      } else {
        score =
            -pvSearch(
                board,
                reduce ? depth - 2 : depth - 1,
                -alpha - 1,
                -alpha,
                ply + 1,
                killers,
                history,
                stop);
        if (reduce && score > alpha) {
          score = -pvSearch(board, depth - 1, -alpha - 1, -alpha, ply + 1, killers, history, stop);
        }
        if (score > alpha && score < beta) {
          score = -pvSearch(board, depth - 1, -beta, -alpha, ply + 1, killers, history, stop);
        }
      }
      board.unmake();

      if (score >= beta) {
        if (!move.isCapture() && !move.isPromotion()) {
          killers.record(move, ply);
          history.record(move, depth);
        }
        transpositionTable.store(
            key, move, depth, Scores.toTable(score, ply), ScoreType.LOWER_BOUND);
        return score;
      }
      if (score > best) {
        best = score;
        bestMove = move;
      }
      if (score > alpha) {
        alpha = score;
      }
    }

    ScoreType type = best <= alphaOriginal ? ScoreType.UPPER_BOUND : ScoreType.EXACT;
    transpositionTable.store(key, bestMove, depth, Scores.toTable(best, ply), type);
    return best;
  }

  private boolean hasNonPawnMaterial(Board board, PieceColor color) {
    for (int index = 0; index < 64; index++) {
      int piece = board.pieceAt(index);
      if (piece != Piece.NONE && Piece.color(piece) == color) {
        PieceType type = Piece.type(piece);
        if (type != PieceType.PAWN && type != PieceType.KING) {
          return true;
        }
      }
    }
    return false;
  }

  private Move transpositionMoveFor(Board board) {
    Entry entry = transpositionTable.probe(board.zobristKey());
    return entry == null ? null : entry.move();
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
