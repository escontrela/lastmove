package com.escontrela.lastmove.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChessGameTest {

  private final ChessGameFactory gameFactory =
      new ChessGameFactory(new ChesspressoRulesEngine());

  @Test
  void playsAProgressiveGameThroughTheAggregateApi() {
    ChessGame game = newGame();

    assertEquals(PieceColor.WHITE, game.currentTurn());

    assertAccepted(game.move(move("e2", "e4")), "e4");
    verifyState(game, PieceColor.BLACK, false, false, false);

    assertAccepted(game.move(move("e7", "e5")), "e5");
    verifyState(game, PieceColor.WHITE, false, false, false);

    assertAccepted(game.move(move("g1", "f3")), "Nf3");
    verifyState(game, PieceColor.BLACK, false, false, false);

    assertEquals(
        List.of("e4", "e5", "Nf3"),
        game.moveHistory().stream().map(ply -> ply.move().san().getValue()).toList());
    assertEquals(game.currentPosition(), game.moveHistory().getLast().resultingPosition());
  }

  @Test
  void playsARawGameUsingStandardAlgebraicNotation() {
    ChessGame game = newGame();

    assertAccepted(game.move("f3"), "f3");
    verifyState(game, PieceColor.BLACK, false, false, false);

    assertAccepted(game.move("e5"), "e5");
    verifyState(game, PieceColor.WHITE, false, false, false);

    assertAccepted(game.move("g4"), "g4");
    verifyState(game, PieceColor.BLACK, false, false, false);

    assertAccepted(game.move("Qh4#"), "Qh4#");
    verifyState(game, PieceColor.WHITE, true, true, false);

    assertEquals(GameResult.BLACK_WINS, game.result().orElseThrow());
    assertEquals(
        List.of("f3", "e5", "g4", "Qh4#"),
        game.moveHistory().stream().map(ply -> ply.move().san().getValue()).toList());
  }

  @Test
  void illegalSanDoesNotChangePositionOrOfficialHistory() {
    ChessGame game = newGame();
    PositionSnapshot initialPosition = game.currentPosition();

    MoveExecutionResult rejected = game.move("Qh5");

    assertFalse(rejected.accepted());
    assertEquals("Illegal SAN move: Qh5", rejected.rejectionReason().orElseThrow());
    assertEquals(initialPosition, game.currentPosition());
    assertTrue(game.moveHistory().isEmpty());
  }

  @Test
  void rejectedMoveDoesNotChangePositionOrOfficialHistory() {
    ChessGame game = newGame();
    PositionSnapshot initialPosition = game.currentPosition();

    MoveExecutionResult rejected = game.move(move("e2", "e5"));

    assertFalse(rejected.accepted());
    assertEquals(initialPosition, game.currentPosition());
    assertTrue(game.moveHistory().isEmpty());
    assertEquals(PieceColor.WHITE, game.currentTurn());
  }

  @Test
  void mateFinishesTheGameAndPreventsLaterMoves() {
    ChessGame game = newGame();

    assertTrue(game.move(move("f2", "f3")).accepted());
    assertTrue(game.move(move("e7", "e5")).accepted());
    assertTrue(game.move(move("g2", "g4")).accepted());
    MoveExecutionResult mate = game.move(move("d8", "h4"));

    assertTrue(mate.accepted());
    verifyState(game, PieceColor.WHITE, true, true, false);
    assertEquals(GameResult.BLACK_WINS, game.result().orElseThrow());

    MoveExecutionResult afterMate = game.move(move("e2", "e4"));
    assertFalse(afterMate.accepted());
    assertEquals(4, game.moveHistory().size());
    assertEquals("The game has already finished", afterMate.rejectionReason().orElseThrow());
    assertEquals(GameResult.BLACK_WINS, game.toRecord().result().orElseThrow());

    TakebackRequest takeback = game.requestTakeback(PieceColor.WHITE, 2);
    takeback.accept(PieceColor.BLACK);
    game.takeBack(takeback);
    assertTrue(game.result().isEmpty());
    assertFalse(game.isCheckmate());
    assertEquals(2, game.moveHistory().size());
  }

  @Test
  void acceptedTakebackRestoresPositionTurnAndBothClocks() {
    ChessGame game = newGame();
    PositionSnapshot initialPosition = game.initialPosition();

    assertTrue(game.move(move("e2", "e4"), Duration.ofMinutes(1)).accepted());
    assertTrue(game.move(move("e7", "e5"), Duration.ofSeconds(30)).accepted());
    assertEquals(
        Duration.ofMinutes(14).plusSeconds(10),
        game.currentClock().remaining(PieceColor.WHITE).orElseThrow());
    assertEquals(
        Duration.ofMinutes(14).plusSeconds(40),
        game.currentClock().remaining(PieceColor.BLACK).orElseThrow());

    TakebackRequest request = game.requestTakeback(PieceColor.WHITE, 2);
    assertThrows(IllegalStateException.class, () -> game.takeBack(request));
    request.accept(PieceColor.BLACK);

    assertEquals(initialPosition, game.takeBack(request));
    assertEquals(TakebackStatus.APPLIED, request.status());
    assertTrue(game.moveHistory().isEmpty());
    assertEquals(PieceColor.WHITE, game.currentTurn());
    assertEquals(Duration.ofMinutes(15), game.currentClock().whiteRemaining().orElseThrow());
    assertEquals(Duration.ofMinutes(15), game.currentClock().blackRemaining().orElseThrow());
  }

  @Test
  void takebackCannotBeAnsweredByTheRequesterOrAppliedAfterTheGameAdvances() {
    ChessGame game = newGame();
    assertTrue(game.move(move("e2", "e4")).accepted());
    TakebackRequest request = game.requestTakeback(PieceColor.WHITE, 1);

    assertThrows(IllegalArgumentException.class, () -> request.accept(PieceColor.WHITE));
    assertTrue(game.move(move("e7", "e5")).accepted());
    request.accept(PieceColor.BLACK);

    assertThrows(IllegalStateException.class, () -> game.takeBack(request));
    assertEquals(2, game.moveHistory().size());
  }

  @Test
  void resignationAndTimeoutProduceExplicitTerminationReasons() {
    ChessGame resigned = newGame();
    assertEquals(GameResult.BLACK_WINS, resigned.resign(PieceColor.WHITE));
    assertEquals(GameTerminationReason.RESIGNATION, resigned.terminationReason().orElseThrow());

    ChessGame expired = newGame();
    assertEquals(GameResult.BLACK_WINS, expired.timeout(PieceColor.WHITE));
    assertEquals(GameTerminationReason.TIMEOUT, expired.terminationReason().orElseThrow());
    assertEquals(Duration.ZERO, expired.currentClock().whiteRemaining().orElseThrow());
  }

  private ChessGame newGame() {
    return gameFactory.createInitial(
        new Player("Alice", PieceColor.WHITE),
        new Player("Bob", PieceColor.BLACK),
        Optional.of(TimeControl.fifteenPlusTen()));
  }

  private MoveCommand move(String from, String to) {
    return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
  }

  private void assertAccepted(MoveExecutionResult result, String expectedSan) {
    assertTrue(result.accepted());
    assertEquals(expectedSan, result.move().orElseThrow().san().getValue());
  }

  private void verifyState(
      ChessGame game,
      PieceColor expectedTurn,
      boolean expectedCheck,
      boolean expectedMate,
      boolean expectedStalemate) {
    GameStateSnapshot state = game.currentState();
    assertEquals(expectedTurn, state.whoseTurn());
    assertEquals(expectedCheck, state.check());
    assertEquals(expectedMate, state.mate());
    assertEquals(expectedStalemate, state.stalemate());
  }
}
