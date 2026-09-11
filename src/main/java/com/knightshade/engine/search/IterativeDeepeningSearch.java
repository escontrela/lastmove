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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Iterative search: iterative deepening with principal variation search, aspiration windows, null-move
 * pruning and late move reductions, backed by a transposition table, killer moves and the history
 * heuristic.
 */
public final class IterativeDeepeningSearch implements Search {

  private static final int DEFAULT_MAX_DEPTH = 64;
  private static final int ASPIRATION_DELTA = 25;
  private static final int MAX_CHECK_EXTENSIONS = 2;

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
    return search(board, limits, stop, Map.of());
  }

  @Override
  public SearchResult search(
      Board board,
      SearchLimits limits,
      StopSignal stop,
      Map<Long, Integer> positionOccurrences) {
    Objects.requireNonNull(board, "board must not be null");
    Objects.requireNonNull(limits, "limits must not be null");
    Objects.requireNonNull(stop, "stop must not be null");
    Map<Long, Integer> repetitions =
        new HashMap<>(
            Objects.requireNonNull(
                positionOccurrences, "positionOccurrences must not be null"));
    repetitions.putIfAbsent(board.zobristKey(), 1);

    // Scores depend on the supplied game history; reuse entries only within this search.
    transpositionTable.clear();
    bestRootMove = null;
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
    StopSignal timeBoundStop = () -> stop.shouldStop() || timeManager.exceeded();

    Move bestMove = rootMoves.getFirst();
    int bestScore = 0;
    int completedDepth = 0;
    for (int depth = 1; depth <= maxDepth; depth++) {
      int score =
          searchDepth(
              board,
              depth,
              bestScore,
              killers,
              history,
              repetitions,
              timeBoundStop);
      if (timeBoundStop.shouldStop()) {
        break;
      }
      completedDepth = depth;
      bestScore = score;
      bestMove = bestRootMove;
      if (Scores.isMate(score)) {
        break;
      }
    }
    return new SearchResult(
        bestMove, bestScore, completedDepth, totalNodes(), elapsedMillis(startedAt));
  }

  private int searchDepth(
      Board board,
      int depth,
      int previousScore,
      KillerMoves killers,
      HistoryTable history,
      Map<Long, Integer> repetitions,
      StopSignal stop) {
    int delta = ASPIRATION_DELTA;
    int alpha = depth <= 1 ? -Scores.INF : Math.max(-Scores.INF, previousScore - delta);
    int beta = depth <= 1 ? Scores.INF : Math.min(Scores.INF, previousScore + delta);
    while (true) {
      if (stop.shouldStop()) {
        return previousScore;
      }
      int score =
          searchRoot(
              board,
              depth,
              alpha,
              beta,
              killers,
              history,
              repetitions,
              stop);
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
      Map<Long, Integer> repetitions,
      StopSignal stop) {
    // Retain the previous iteration/aspiration attempt's best move explicitly at the root.
    Move ttMove = bestRootMove;
    List<Move> moves =
        moveOrderer.order(
            board,
            moveGenerator.generate(board),
            new OrderingContext(0, killers, history, ttMove));
    int best = -Scores.INF;
    int moveCount = 0;
    for (Move move : moves) {
      if (stop.shouldStop()) {
        break;
      }
      board.make(move);
      long childKey = board.zobristKey();
      moveCount++;
      recordPosition(repetitions, childKey);
      int score;
      if (moveCount == 1) {
        score =
            -pvSearch(
                board,
                depth - 1,
                -beta,
                -alpha,
                1,
                0,
                killers,
                history,
                repetitions,
                false,
                stop);
      } else {
        score =
            -pvSearch(
                board,
                depth - 1,
                -alpha - 1,
                -alpha,
                1,
                0,
                killers,
                history,
                repetitions,
                false,
                stop);
        if (score > alpha && score < beta) {
          score =
              -pvSearch(
                  board,
                  depth - 1,
                  -beta,
                  -alpha,
                  1,
                  0,
                  killers,
                  history,
                  repetitions,
                  false,
                  stop);
        }
      }
      forgetPosition(repetitions, childKey);
      board.unmake();
      if (stop.shouldStop()) {
        break;
      }
      if (score > best) {
        best = score;
        bestRootMove = move;
      }
      if (score > alpha) {
        alpha = score;
      }
      if (alpha >= beta) {
        break;
      }
    }
    return best;
  }

  /** Searches one root alternative using this worker's private search state. */
  int searchRootMove(
      Board board, Move move, int depth, int alpha, int beta, boolean scout,
      KillerMoves killers, HistoryTable history, Map<Long, Integer> repetitions, StopSignal stop) {
    board.make(move);
    long key = board.zobristKey();
    recordPosition(repetitions, key);
    try {
      int score = -pvSearch(board, depth - 1, scout ? -alpha - 1 : -beta, -alpha,
          1, 0, killers, history, repetitions, false, stop);
      if (scout && score > alpha && score < beta && !stop.shouldStop()) {
        score = -pvSearch(board, depth - 1, -beta, -alpha,
            1, 0, killers, history, repetitions, false, stop);
      }
      return score;
    } finally {
      forgetPosition(repetitions, key);
      board.unmake();
    }
  }

  long nodesVisited() {
    return totalNodes();
  }

  private int pvSearch(
      Board board,
      int depth,
      int alpha,
      int beta,
      int ply,
      int checkExtensions,
      KillerMoves killers,
      HistoryTable history,
      Map<Long, Integer> repetitions,
      boolean nullBranch,
      StopSignal stop) {
    nodes++;
    if (stop.shouldStop()) {
      return alpha;
    }
    if (ply >= Scores.MAX_PLY) {
      return evaluateFromSideToMove(board);
    }
    if (!nullBranch && repetitions.getOrDefault(board.zobristKey(), 0) >= 3) {
      return 0;
    }

    boolean inCheck = board.inCheck(board.sideToMove());
    if (!nullBranch && board.halfmoveClock() >= 100) {
      return inCheck && !moveGenerator.hasLegalMove(board) ? -(Scores.MATE - ply) : 0;
    }
    // No continuation can mate sooner than the current ply (or lose later than this bound).
    alpha = Math.max(alpha, -Scores.MATE + ply);
    beta = Math.min(beta, Scores.MATE - ply - 1);
    if (alpha >= beta) {
      return alpha;
    }
    if (inCheck && depth > 0 && checkExtensions < MAX_CHECK_EXTENSIONS) {
      depth++;
      checkExtensions++;
    }

    long key = tableKey(board);
    boolean useTable = !nullBranch && repetitions.getOrDefault(board.zobristKey(), 0) <= 1;
    Entry entry = useTable ? transpositionTable.probe(key) : null;
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

    if (depth <= 0) {
      return quiescence.searchWithQuietChecks(
          board, alpha, beta, ply, stop, nullBranch ? null : repetitions);
    }

    List<Move> legal = moveGenerator.generate(board);
    if (legal.isEmpty()) {
      return inCheck ? -(Scores.MATE - ply) : 0;
    }
    if (!nullBranch
        && !inCheck
        && !Scores.isMate(beta)
        && beta - alpha == 1
        && depth >= 3
        && hasNonPawnMaterial(board, board.sideToMove())
        && evaluateFromSideToMove(board) >= beta) {
      int reduction = 2 + depth / 4;
      board.makeNullMove();
      int score =
          -pvSearch(
              board,
              depth - 1 - reduction,
              -beta,
              -beta + 1,
              ply + 1,
              checkExtensions,
              killers,
              history,
              repetitions,
              true,
              stop);
      board.unmakeNullMove();
      if (stop.shouldStop()) {
        return alpha;
      }
      if (score >= beta && !Scores.isMate(score)) {
        if (useTable) {
          transpositionTable.store(
              key, null, depth, Scores.toTable(score, ply), ScoreType.LOWER_BOUND);
        }
        return score;
      }
    }

    // A successful null-move cutoff needs no move scoring or SEE calculations.
    legal = moveOrderer.order(
        board, legal, new OrderingContext(ply, killers, history, ttMove));

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
      long childKey = board.zobristKey();
      recordPosition(repetitions, childKey);
      boolean givesCheck = board.inCheck(board.sideToMove());
      boolean reduced = reduce && !givesCheck;
      int score;
      if (moveCount == 1) {
        score =
            -pvSearch(
                board,
                depth - 1,
                -beta,
                -alpha,
                ply + 1,
                checkExtensions,
                killers,
                history,
                repetitions,
                nullBranch,
                stop);
      } else {
        score =
            -pvSearch(
                board,
                reduced ? depth - 2 : depth - 1,
                -alpha - 1,
                -alpha,
                ply + 1,
                checkExtensions,
                killers,
                history,
                repetitions,
                nullBranch,
                stop);
        if (reduced && score > alpha) {
          score =
              -pvSearch(
                  board,
                  depth - 1,
                  -alpha - 1,
                  -alpha,
                  ply + 1,
                  checkExtensions,
                  killers,
                  history,
                  repetitions,
                  nullBranch,
                  stop);
        }
        if (score > alpha && score < beta) {
          score =
              -pvSearch(
                  board,
                  depth - 1,
                  -beta,
                  -alpha,
                  ply + 1,
                  checkExtensions,
                  killers,
                  history,
                  repetitions,
                  nullBranch,
                  stop);
        }
      }
      forgetPosition(repetitions, childKey);
      board.unmake();
      if (stop.shouldStop()) {
        return alpha;
      }

      if (score >= beta) {
        if (!move.isCapture() && !move.isPromotion()) {
          killers.record(move, ply);
          history.record(board.sideToMove(), move, depth);
          for (int i = 0; i < moveCount - 1; i++) {
            history.penalize(board.sideToMove(), legal.get(i), depth);
          }
        }
        if (useTable) {
          transpositionTable.store(
              key, move, depth, Scores.toTable(score, ply), ScoreType.LOWER_BOUND);
        }
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
    if (useTable) {
      transpositionTable.store(key, bestMove, depth, Scores.toTable(best, ply), type);
    }
    return best;
  }

  private void recordPosition(Map<Long, Integer> repetitions, long key) {
    repetitions.merge(key, 1, Integer::sum);
  }

  private void forgetPosition(Map<Long, Integer> repetitions, long key) {
    repetitions.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
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

  // The fifty-move counter affects a search score, but is deliberately absent from repetition keys.
  private long tableKey(Board board) {
    return board.zobristKey() ^ (0x9E3779B97F4A7C15L * board.halfmoveClock());
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
