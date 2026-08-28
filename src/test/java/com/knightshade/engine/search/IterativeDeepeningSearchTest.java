package com.knightshade.engine.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.api.SearchLimits;
import com.knightshade.engine.api.SearchResult;
import com.knightshade.engine.api.StopSignal;
import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.escontrela.lastmove.domain.common.Square;
import com.knightshade.engine.evaluation.PieceSquareEvaluator;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IterativeDeepeningSearchTest {

  @Test
  void findsAMateInOne() {
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(FenParser.parse("6k1/5ppp/8/8/8/8/8/4R2K w - - 0 1"),
            SearchLimits.depth(4), StopSignal.never());

    assertNotNull(result.move());
    assertEquals("e1e8", result.move().toUci());
  }

  @Test
  void returnsNullMoveWhenCheckmated() {
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(FenParser.parse("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"),
            SearchLimits.depth(4), StopSignal.never());

    assertNull(result.move());
  }

  @Test
  void isDeterministicAcrossFreshInstances() {
    String fen = "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3";

    var first = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());
    var second = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult a = first.search(FenParser.parse(fen), SearchLimits.depth(4), StopSignal.never());
    SearchResult b = second.search(FenParser.parse(fen), SearchLimits.depth(4), StopSignal.never());

    assertNotNull(a.move());
    assertEquals(a.move(), b.move());
  }

  @Test
  void capturesAHangingQueen() {
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(FenParser.parse("7k/8/8/4q3/8/8/8/4R2K w - - 0 1"),
            SearchLimits.depth(4), StopSignal.never());

    assertNotNull(result.move());
    assertEquals("e1e5", result.move().toUci());
  }

  @Test
  void movesAnAttackedQueenInsteadOfLeavingItEnPriseAtShallowDepth() {
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(
            FenParser.parse("7k/8/8/2n5/4Q3/8/8/7K w - - 0 1"),
            SearchLimits.depth(1),
            StopSignal.never());

    assertNotNull(result.move());
    assertEquals(Square.of("e4"), result.move().from());
  }

  @Test
  void avoidsCompletingAThirdRepetitionWhenItsPositionIsNotWorse() {
    Board board = FenParser.parse("7k/8/8/4r3/8/8/8/4Q2K w - - 0 1");
    Move queenTakesRook = move(board, "e1e5");
    long repeatedPosition = resultingKey(board, queenTakesRook);
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(
            board,
            SearchLimits.depth(1),
            StopSignal.never(),
            Map.of(board.zobristKey(), 1, repeatedPosition, 2));

    assertNotNull(result.move());
    assertTrue(!queenTakesRook.equals(result.move()));
  }

  @Test
  void acceptsAThirdRepetitionAsADefensiveResourceWhenItsPositionIsWorse() {
    Board board = FenParser.parse("7k/8/8/4q3/8/8/8/4R2K w - - 0 1");
    Move rookTakesQueen = move(board, "e1e5");
    long repeatedPosition = resultingKey(board, rookTakesQueen);
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(
            board,
            SearchLimits.depth(1),
            StopSignal.never(),
            Map.of(board.zobristKey(), 1, repeatedPosition, 2));

    assertEquals(rookTakesQueen, result.move());
    assertEquals(0, result.score());
  }

  @Test
  void findsALegalMoveInAQueenEndgameWithinTheTimeBudget() {
    String fen = "8/1K5k/5q2/2p5/8/8/8/8 b - - 7 69";
    var board = FenParser.parse(fen);
    var legal = new LegalMoveGenerator().generate(board);
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(board, SearchLimits.timeOnly(Duration.ofSeconds(1)), StopSignal.never());

    assertNotNull(result.move());
    assertTrue(legal.stream().anyMatch(move -> move.equals(result.move())));
  }

  @Test
  void respectsTheTimeBudgetAndReturnsALegalMove() {
    String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    var board = FenParser.parse(fen);
    var legal = new LegalMoveGenerator().generate(board);
    var search = new IterativeDeepeningSearch(new LegalMoveGenerator(), new PieceSquareEvaluator());

    SearchResult result =
        search.search(board, SearchLimits.timeOnly(Duration.ofMillis(20)), StopSignal.never());

    assertNotNull(result.move());
    assertTrue(legal.stream().anyMatch(move -> move.equals(result.move())));
  }

  private Move move(Board board, String uci) {
    return new LegalMoveGenerator().generate(board).stream()
        .filter(candidate -> candidate.toUci().equals(uci))
        .findFirst()
        .orElseThrow();
  }

  private long resultingKey(Board board, Move move) {
    board.make(move);
    long key = board.zobristKey();
    board.unmake();
    return key;
  }
}
