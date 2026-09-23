package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.HashMap;

import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.evaluation.PieceSquareEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import com.knightshade.engine.movegen.MoveGenerator;
import com.knightshade.engine.ordering.MvvLvaMoveOrderer;
import com.knightshade.engine.see.See;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QuiescenceSearchTest {

  @Test
  void recognizesStalemateBeforeStandPatEvenWithANarrowWindow() {
    Board board = FenParser.parse("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
    var search = new QuiescenceSearch(
        new LegalMoveGenerator(), position -> -900, new MvvLvaMoveOrderer());
    assertEquals(0, search.search(board, -20, 20, 0, StopSignal.never()));
  }

  @Test
  void returnsDrawForStalemateWhenStandPatIsBelowBeta() {
    Board board = FenParser.parse("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
    var search = new QuiescenceSearch(
        new LegalMoveGenerator(), position -> -10, new MvvLvaMoveOrderer());

    assertEquals(0, search.search(board, -20, 20, 0, StopSignal.never()));
  }

  @Test
  void standPatCutoffChecksStalemateWithoutGeneratingAFullMoveList() {
    Board board = FenParser.parse("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
    CountingMoveGenerator generator = new CountingMoveGenerator();
    var search = new QuiescenceSearch(generator, position -> -900, new MvvLvaMoveOrderer());

    assertEquals(0, search.search(board, -20, 20, 0, StopSignal.never()));
    assertEquals(1, generator.legalMoveQueries);
    assertEquals(0, generator.fullGenerations);
    assertEquals(0, generator.captureGenerations);
  }

  @Test
  void recognizesFiftyMoveDrawButCheckmateTakesPrecedence() {
    var search = new QuiescenceSearch(
        new LegalMoveGenerator(), new PieceSquareEvaluator(), new MvvLvaMoveOrderer());
    assertEquals(0, search.search(FenParser.parse("7k/8/6Q1/8/8/8/8/K7 b - - 100 80"),
        -Scores.INF, Scores.INF, 0, StopSignal.never()));
    assertEquals(-Scores.MATE, search.search(
        FenParser.parse("7k/6Q1/5K2/8/8/8/8/8 b - - 100 80"),
        -Scores.INF, Scores.INF, 0, StopSignal.never()));
  }

  @Test
  void tracksRepetitionAtTheHorizonAndRestoresHistory() {
    Board board = FenParser.parse("7k/8/8/8/8/8/8/K6R w - - 0 1");
    var history = new HashMap<Long, Integer>();
    history.put(board.zobristKey(), 3);
    var before = new HashMap<>(history);
    var search = new QuiescenceSearch(
        new LegalMoveGenerator(), new PieceSquareEvaluator(), new MvvLvaMoveOrderer());
    assertEquals(0, search.searchWithQuietChecks(
        board, -Scores.INF, Scores.INF, 0, StopSignal.never(), history));
    assertEquals(before, history);
  }

  @Test
  void searchesACaptureOfAMajorPieceEvenWhenSeeMarksTheExchangeAsLosing() {
    Board board = FenParser.parse("6k1/2p5/3r4/4Q3/8/8/8/7K w - - 0 1");
    LegalMoveGenerator generator = new LegalMoveGenerator();
    Move queenTakesRook =
        generator.generateCaptures(board).stream()
            .filter(move -> move.toUci().equals("e5d6"))
            .findFirst()
            .orElseThrow();
    assertTrue(See.evaluate(board, queenTakesRook) < 0);

    QuiescenceSearch search =
        new QuiescenceSearch(generator, new PieceSquareEvaluator(), new MvvLvaMoveOrderer());
    search.search(board, -Scores.INF, Scores.INF, 0, StopSignal.never());

    assertTrue(search.nodes() >= 3, "the queen-for-rook line and its recapture must be visited");
  }

  @Test
  void restoresBoardAndRepetitionHistoryWhenCancelledInAQuietnessChild() {
    Board board = FenParser.parse("6k1/2p5/3r4/4Q3/8/8/8/7K w - - 0 1");
    String fen = board.toFen();
    long key = board.zobristKey();
    var history = new HashMap<Long, Integer>();
    history.put(key, 1);
    var beforeHistory = new HashMap<>(history);
    AtomicInteger polls = new AtomicInteger();
    StopSignal stop = () -> polls.incrementAndGet() >= 2;
    var search = new QuiescenceSearch(
        new LegalMoveGenerator(), new PieceSquareEvaluator(), new MvvLvaMoveOrderer());

    search.searchWithQuietChecks(board, -Scores.INF, Scores.INF, 0, stop, history);

    assertTrue(polls.get() >= 2);
    assertEquals(fen, board.toFen());
    assertEquals(key, board.zobristKey());
    assertEquals(beforeHistory, history);
  }

  private static final class CountingMoveGenerator implements MoveGenerator {
    private final LegalMoveGenerator delegate = new LegalMoveGenerator();
    private int fullGenerations;
    private int captureGenerations;
    private int legalMoveQueries;

    @Override
    public List<Move> generate(Board board) {
      fullGenerations++;
      return delegate.generate(board);
    }

    @Override
    public List<Move> generateCaptures(Board board) {
      captureGenerations++;
      return delegate.generateCaptures(board);
    }

    @Override
    public boolean hasLegalMove(Board board) {
      legalMoveQueries++;
      return delegate.hasLegalMove(board);
    }
  }
}
