package com.escontrela.lastmove.domain.training.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemoryGameTest {

  @Test
  void startsReadyAndEntersMemorizationWithAnUntouchedScore() {
    MemoryGame session = new MemoryGame(1);

    assertEquals(MemoryGameState.READY, session.state());
    assertEquals(Duration.ofMinutes(3), session.remainingTime());
    assertEquals(1, session.attempt());

    session.start();

    assertEquals(MemoryGameState.MEMORIZING, session.state());
    assertEquals(0, session.score());
    assertEquals(0, session.maxPossibleScore());
  }

  @Test
  void fixesDifficultyWhenGuessingBegins() {
    MemoryGame session = startedMemoryGame();

    assertTrue(session.completeMemorization(Duration.ofSeconds(90)));

    assertEquals(MemoryGameState.GUESSING, session.state());
    assertEquals(MemoryGameDifficulty.TWO_PIECES, session.activeDifficulty().orElseThrow());
    assertEquals(Duration.ofSeconds(90), session.remainingTime());
  }

  @Test
  void awardsPartialCreditAndCountsEveryEvaluatedPiece() {
    MemoryGame session = startedMemoryGame();
    session.completeMemorization(Duration.ofSeconds(90));

    assertTrue(session.submitEvaluation(1, Duration.ofSeconds(100)));

    assertEquals(1, session.score());
    assertEquals(2, session.maxPossibleScore());
    assertEquals(0.5d, session.successRate());
    assertEquals(MemoryGameState.MEMORIZING, session.state());
    assertTrue(session.activeDifficulty().isEmpty());
  }

  @Test
  void finishesAtExactlyThreeMinutesWithoutCountingTheActiveRound() {
    MemoryGame session = startedMemoryGame();
    session.completeMemorization(Duration.ofSeconds(175));

    assertFalse(session.submitEvaluation(3, Duration.ofMinutes(3)));

    assertEquals(MemoryGameState.FINISHED, session.state());
    assertEquals(Duration.ZERO, session.remainingTime());
    assertEquals(0, session.score());
    assertEquals(0, session.maxPossibleScore());
    assertTrue(session.activeDifficulty().isEmpty());
  }

  @Test
  void globalLimitAlsoExpiresDuringMemorization() {
    MemoryGame session = startedMemoryGame();

    session.updateElapsedTime(Duration.ofMinutes(3).plusSeconds(4));

    assertEquals(MemoryGameState.FINISHED, session.state());
    assertEquals(Duration.ofMinutes(3), session.elapsedTime());
    assertEquals(Duration.ZERO, session.remainingTime());
  }

  @Test
  void submittedAnswersBeforeExpiryRemainInTheFinalResult() {
    MemoryGame session = startedMemoryGame();
    session.completeMemorization(Duration.ofSeconds(5));
    session.submitEvaluation(1, Duration.ofSeconds(20));
    session.completeMemorization(Duration.ofSeconds(25));

    session.updateElapsedTime(Duration.ofMinutes(3));

    assertEquals(1, session.score());
    assertEquals(1, session.maxPossibleScore());
  }

  @Test
  void sixtyPercentIsSuccessfulAndAnythingBelowItIsNot() {
    MemoryGame successful = startedMemoryGame();
    evaluate(successful, Duration.ofSeconds(5), 1);
    evaluate(successful, Duration.ofSeconds(90), 1);
    evaluate(successful, Duration.ofSeconds(100), 1);
    assertEquals(3, successful.score());
    assertEquals(5, successful.maxPossibleScore());
    assertEquals(0.60d, successful.successRate());
    assertTrue(successful.isSuccessful());

    MemoryGame unsuccessful = startedMemoryGame();
    evaluate(unsuccessful, Duration.ofSeconds(5), 0);
    evaluate(unsuccessful, Duration.ofSeconds(90), 1);
    assertEquals(1.0d / 3.0d, unsuccessful.successRate());
    assertFalse(unsuccessful.isSuccessful());
  }

  @Test
  void zeroEvaluatedRoundsHasAZeroRateAndIsNotSuccessful() {
    MemoryGame session = startedMemoryGame();
    session.updateElapsedTime(Duration.ofMinutes(3));

    assertEquals(0.0d, session.successRate());
    assertFalse(session.isSuccessful());
  }

  @Test
  void enforcesAttemptAndTransitionInvariants() {
    assertThrows(IllegalArgumentException.class, () -> new MemoryGame(0));
    assertThrows(IllegalArgumentException.class, () -> new MemoryGame(3));

    MemoryGame session = new MemoryGame(2);
    assertThrows(
        IllegalStateException.class,
        () -> session.completeMemorization(Duration.ofSeconds(5)));
    session.start();
    assertThrows(IllegalStateException.class, session::start);
  }

  @Test
  void rejectsInvalidScoresAndTimeMovingBackwards() {
    MemoryGame session = startedMemoryGame();
    session.completeMemorization(Duration.ofSeconds(90));

    assertThrows(
        IllegalArgumentException.class,
        () -> session.submitEvaluation(3, Duration.ofSeconds(91)));
    assertEquals(Duration.ofSeconds(90), session.elapsedTime());
    assertThrows(
        IllegalArgumentException.class,
        () -> session.updateElapsedTime(Duration.ofSeconds(89)));
  }

  private static MemoryGame startedMemoryGame() {
    MemoryGame session = new MemoryGame(1);
    session.start();
    return session;
  }

  private static void evaluate(
      MemoryGame session, Duration guessingStartedAt, int correctPieces) {
    assertTrue(session.completeMemorization(guessingStartedAt));
    assertTrue(session.submitEvaluation(correctPieces, guessingStartedAt.plusSeconds(1)));
  }
}
