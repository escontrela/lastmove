package com.escontrela.lastmove.application.training.storm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.analysis.AnalysisTree;
import com.escontrela.lastmove.domain.common.*;
import com.escontrela.lastmove.domain.game.*;
import com.escontrela.lastmove.domain.notation.SanMove;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.*;
import com.escontrela.lastmove.domain.training.storm.StormGame;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Instant; import java.util.*;
import org.junit.jupiter.api.Test;

class StormGamePuzzleRunnerTest {
  @Test void countsOneHitOnlyAfterTheEntireMultiMovePuzzleIsSolved() {
    ChessGameFactory factory = new ChessGameFactory(new ChesspressoRulesEngine());
    PositionSnapshot initial = new ChesspressoRulesEngine().startingPosition();
    ChessGame firstGame = factory.createAnalysisGame(initial);
    MoveExecutionResult e4 = firstGame.move(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    ChessGame replyGame = factory.createAnalysisGame(e4.newSnapshot());
    MoveExecutionResult e5 = replyGame.move(new MoveCommand(Square.of("e7"), Square.of("e5"), Optional.empty()));
    ChessGame finalGame = factory.createAnalysisGame(e5.newSnapshot());
    MoveExecutionResult bishopC4 = finalGame.move(new MoveCommand(Square.of("f1"), Square.of("c4"), Optional.empty()));
    AnalysisTree tree = new AnalysisTree();
    var root = tree.addRoot(new Ply(UUID.randomUUID(), e4.move().orElseThrow(), e4.newSnapshot(), 1, PieceColor.WHITE));
    var reply = tree.addChild(root.id(), new Ply(UUID.randomUUID(), e5.move().orElseThrow(), e5.newSnapshot(), 1, PieceColor.BLACK));
    tree.addChild(reply.id(), new Ply(UUID.randomUUID(), bishopC4.move().orElseThrow(), bishopC4.newSnapshot(), 2, PieceColor.WHITE));
    TacticExercise exercise = new TacticExercise(TacticExerciseId.random(), "Two solver moves", new AnalysisContent(initial, Optional.empty(), tree), Instant.now(), Instant.now());
    TacticSuiteId suite = TacticSuiteId.random();
    StormGamePuzzleRunner runner = new StormGamePuzzleRunner(() -> List.of(new TacticExerciseReference(PlayerId.of(1L), suite, exercise)), factory, new StormGame());
    runner.start(new StormGameChallenge(PlayerId.of(1L), suite, exercise.id(), exercise.title(), initial, PieceColor.WHITE));

    StormGameMoveOutcome firstMove = runner.attemptMove(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    assertFalse(firstMove.feedback().solved());
    assertEquals(0, firstMove.snapshot().correctAnswers());
    assertEquals(0, firstMove.snapshot().finalizedPuzzles());

    StormGameMoveOutcome finalMove = runner.attemptMove(new MoveCommand(Square.of("f1"), Square.of("c4"), Optional.empty()));
    assertTrue(finalMove.feedback().solved());
    assertEquals(1, finalMove.snapshot().correctAnswers());
    assertEquals(1, finalMove.snapshot().finalizedPuzzles());
  }

  @Test void keepsAnIncorrectPuzzleRetryableAndResolvesTheCorrectLine() {
    ChessGameFactory factory = new ChessGameFactory(new ChesspressoRulesEngine());
    PositionSnapshot initial = new ChesspressoRulesEngine().startingPosition(); ChessGame game = factory.createAnalysisGame(initial);
    MoveExecutionResult move = game.move(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    AnalysisTree tree = new AnalysisTree(); tree.addRoot(new Ply(UUID.randomUUID(), move.move().orElseThrow(), move.newSnapshot(), 1, PieceColor.WHITE));
    TacticExercise exercise = new TacticExercise(TacticExerciseId.random(), "Line", new AnalysisContent(initial, Optional.empty(), tree), Instant.now(), Instant.now());
    TacticSuiteId suite = TacticSuiteId.random(); StormGameChallenge challenge = new StormGameChallenge(PlayerId.of(1L), suite, exercise.id(), exercise.title(), initial, PieceColor.WHITE);
    StormGamePuzzleRunner runner = new StormGamePuzzleRunner(() -> List.of(new TacticExerciseReference(PlayerId.of(1L), suite, exercise)), factory, new StormGame());
    runner.start(challenge);
    assertTrue(runner.snapshot().challenge().isPresent());
    var wrong = runner.attemptMove(new MoveCommand(Square.of("d2"), Square.of("d4"), Optional.empty())); assertFalse(wrong.feedback().correct());
    var correct = runner.attemptMove(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty())); assertTrue(correct.feedback().solved());
  }

  @Test void wrongColorMovePlaysAsAnErrorButDoesNotFailThePuzzle() {
    ChessGameFactory factory = new ChessGameFactory(new ChesspressoRulesEngine());
    PositionSnapshot initial = new ChesspressoRulesEngine().startingPosition();
    ChessGame game = factory.createAnalysisGame(initial);
    MoveExecutionResult move = game.move(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    AnalysisTree tree = new AnalysisTree();
    tree.addRoot(new Ply(UUID.randomUUID(), move.move().orElseThrow(), move.newSnapshot(), 1, PieceColor.WHITE));
    TacticExercise exercise = new TacticExercise(TacticExerciseId.random(), "Wrong color", new AnalysisContent(initial, Optional.empty(), tree), Instant.now(), Instant.now());
    TacticSuiteId suite = TacticSuiteId.random();
    StormGamePuzzleRunner runner = new StormGamePuzzleRunner(
        () -> List.of(new TacticExerciseReference(PlayerId.of(1L), suite, exercise)), factory, new StormGame());
    runner.start(new StormGameChallenge(PlayerId.of(1L), suite, exercise.id(), exercise.title(), initial, PieceColor.WHITE));

    StormGameMoveOutcome wrongColor = runner.attemptMove(
        new MoveCommand(Square.of("e7"), Square.of("e5"), Optional.empty()));

    assertFalse(wrongColor.feedback().correct());
    assertEquals(0, wrongColor.snapshot().failedPuzzles());
    assertEquals(0, wrongColor.snapshot().finalizedPuzzles());
  }

  @Test void secondHintHighlightsOriginAndTargetWithoutApplyingTheMove() {
    ChessGameFactory factory = new ChessGameFactory(new ChesspressoRulesEngine());
    PositionSnapshot initial = new ChesspressoRulesEngine().startingPosition();
    ChessGame game = factory.createAnalysisGame(initial);
    MoveExecutionResult e4 = game.move(new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    AnalysisTree tree = new AnalysisTree();
    tree.addRoot(new Ply(UUID.randomUUID(), e4.move().orElseThrow(), e4.newSnapshot(), 1, PieceColor.WHITE));
    TacticExercise exercise = new TacticExercise(TacticExerciseId.random(), "Reveal", new AnalysisContent(initial, Optional.empty(), tree), Instant.now(), Instant.now());
    TacticSuiteId suite = TacticSuiteId.random();
    StormGamePuzzleRunner runner = new StormGamePuzzleRunner(() -> List.of(new TacticExerciseReference(PlayerId.of(1L), suite, exercise)), factory, new StormGame());
    runner.start(new StormGameChallenge(PlayerId.of(1L), suite, exercise.id(), exercise.title(), initial, PieceColor.WHITE));

    assertTrue(runner.requestHint().feedback().hintSquare().isPresent());
    StormGameMoveOutcome revealed = runner.requestHint();
    assertFalse(revealed.feedback().solved());
    assertFalse(revealed.feedback().correct());
    assertEquals(Square.of("e2"), revealed.feedback().hintSquare().orElseThrow());
    assertEquals(Square.of("e4"), revealed.feedback().hintTargetSquare().orElseThrow());
    assertEquals(0, revealed.snapshot().correctAnswers());
    assertEquals(0, revealed.snapshot().finalizedPuzzles());

    StormGameMoveOutcome completed = runner.attemptMove(
        new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    assertTrue(completed.feedback().solved());
    assertEquals(0, completed.snapshot().correctAnswers());
    assertEquals(1, completed.snapshot().finalizedPuzzles());
  }
}
