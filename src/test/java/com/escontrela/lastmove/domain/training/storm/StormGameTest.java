package com.escontrela.lastmove.domain.training.storm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StormGameTest {

  @Test
  void startsReadyAndActivatesOnePuzzle() {
    StormGame game = new StormGame();
    TacticExerciseId puzzle = TacticExerciseId.random();

    assertEquals(StormGameState.READY, game.state());
    assertEquals(Duration.ofMinutes(3), game.remainingTime());
    game.start();
    game.activatePuzzle(puzzle);

    assertEquals(StormGameState.RUNNING, game.state());
    assertEquals(puzzle, game.activePuzzle().orElseThrow());
  }

  @Test
  void firstErrorIsPermanentButSolvedPuzzleIsStillFinalized() {
    StormGame game = runningWith(TacticExerciseId.random());
    TacticExerciseId id = game.activePuzzle().orElseThrow();

    game.markError();
    game.markError();
    game.completePuzzle();

    assertTrue(game.puzzleProgress(id).orElseThrow().failed());
    assertTrue(game.puzzleProgress(id).orElseThrow().solved());
    assertEquals(1, game.puzzlesFinished());
    assertEquals(0, game.hits());
    assertEquals(1, game.errors());
  }

  @Test
  void hintFailsPuzzleAndDoesNotAwardIt() {
    StormGame game = runningWith(TacticExerciseId.random());
    TacticExerciseId id = game.activePuzzle().orElseThrow();

    game.markHintUsed();
    game.completePuzzle();

    assertTrue(game.puzzleProgress(id).orElseThrow().hintUsed());
    assertEquals(0.0d, game.percentage());
    assertFalse(game.isSuccessful());
  }

  @Test
  void exactSixtyPercentPassesAndZeroOfZeroIsZero() {
    StormGame game = new StormGame();
    game.start();
    assertEquals(0.0d, game.percentage());
    assertFalse(game.isSuccessful());

    finish(game, false);
    finish(game, false);
    finish(game, true);
    finish(game, true);
    finish(game, true);

    assertEquals(60.0d, game.percentage());
    assertTrue(game.isSuccessful());
  }

  @Test
  void expiryFinishesAndDoesNotStartAnotherPuzzle() {
    StormGame game = runningWith(TacticExerciseId.random());
    game.updateElapsedTime(Duration.ofMinutes(3));

    assertEquals(StormGameState.FINISHED, game.state());
    assertEquals(Duration.ZERO, game.remainingTime());
    assertThrows(IllegalStateException.class, () -> game.activatePuzzle(TacticExerciseId.random()));
  }

  private static StormGame runningWith(TacticExerciseId id) {
    StormGame game = new StormGame();
    game.start();
    game.activatePuzzle(id);
    return game;
  }

  private static void finish(StormGame game, boolean correct) {
    game.activatePuzzle(TacticExerciseId.random());
    if (!correct) game.markError();
    game.completePuzzle();
  }
}
