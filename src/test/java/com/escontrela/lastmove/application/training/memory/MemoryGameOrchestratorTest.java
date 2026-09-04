package com.escontrela.lastmove.application.training.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.training.memory.MemoryGameDifficulty;
import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemoryGameOrchestratorTest {
  private static final String POSITION_A =
      "rnbqkbnr/pppppppp/8/4P3/8/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
  private static final String POSITION_B =
      "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

  @Test
  void publishesFullPositionThenGuessingAfterFiveSeconds() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A);

    orchestrator.start();
    assertEquals(MemoryGameState.MEMORIZING, last(states).state());
    assertTrue(last(states).showingCompletePosition());
    assertEquals(Duration.ofSeconds(5), last(states).memorizationRemaining());

    clock.advanceTo(Duration.ofSeconds(5));
    assertEquals(MemoryGameState.GUESSING, last(states).state());
    assertEquals(MemoryGameDifficulty.ONE_PIECE, last(states).difficulty().orElseThrow());
    assertFalse(last(states).showingCompletePosition());
  }

  @Test
  void republishesTheGlobalCountdownEverySecond() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A);

    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(2));

    assertEquals(Duration.ofSeconds(178), last(states).remainingTime());
    assertEquals(Duration.ofSeconds(3), last(states).memorizationRemaining());
  }

  @Test
  void evaluatesOneAnswerAndIgnoresDuplicates() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(5));
    Map<Square, MemoryGamePiece> answer = answerFrom(last(states).challenge().orElseThrow());

    clock.advanceTo(Duration.ofSeconds(10));
    orchestrator.submitAnswer(answer);
    orchestrator.submitAnswer(answer);

    assertEquals(1, last(states).score());
    assertEquals(1, last(states).maxPossibleScore());
    assertEquals(MemoryGameState.MEMORIZING, last(states).state());
    assertEquals(1, last(states).feedback().size());
    assertTrue(last(states).feedback().getFirst().correct());
  }

  @Test
  void awardsPartialCreditPerSquareTypeAndColour() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(5));
    orchestrator.submitAnswer(answerFrom(last(states).challenge().orElseThrow()));

    clock.advanceTo(Duration.ofSeconds(145));
    orchestrator.submitAnswer(answerFrom(last(states).challenge().orElseThrow()));
    clock.advanceTo(Duration.ofSeconds(151));
    MemoryGameChallenge challenge = last(states).challenge().orElseThrow();
    assertEquals(3, challenge.hiddenPieces().size());
    Map<Square, MemoryGamePiece> partial = new HashMap<>();
    challenge.hiddenPieces().stream().limit(2).forEach(piece -> partial.put(piece.square(), piece));
    orchestrator.submitAnswer(partial);

    assertEquals(4, last(states).score());
    assertEquals(5, last(states).maxPossibleScore());
    assertEquals(2, last(states).feedback().stream().filter(MemoryGameFeedback::correct).count());
    assertEquals(1, last(states).feedback().stream().filter(feedback -> !feedback.correct()).count());
  }

  @Test
  void feedbackCannotStartAnotherRoundAtTheSessionLimit() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(5));
    clock.advanceTo(Duration.ofMillis(179_500));
    orchestrator.submitAnswer(answerFrom(last(states).challenge().orElseThrow()));

    clock.advanceTo(Duration.ofSeconds(180));

    assertEquals(MemoryGameState.FINISHED, last(states).state());
    assertEquals(Duration.ZERO, last(states).remainingTime());
  }

  @Test
  void changesDifficultyAtNinetySecondsWithoutWaitingInTests() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(5));

    // Answer the first round just before the boundary so the next guessing phase
    // opens past 90 seconds and must be rebuilt at the two-piece difficulty.
    clock.advanceTo(Duration.ofSeconds(88));
    orchestrator.submitAnswer(answerFrom(last(states).challenge().orElseThrow()));
    clock.advanceTo(Duration.ofSeconds(95));

    assertEquals(MemoryGameState.GUESSING, last(states).state());
    assertEquals(MemoryGameDifficulty.TWO_PIECES, last(states).difficulty().orElseThrow());
    assertEquals(2, last(states).challenge().orElseThrow().hiddenPieces().size());
  }

  @Test
  void expiresAtOneHundredEightySecondsAndCancelsPendingCallbacks() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();

    clock.advanceTo(Duration.ofSeconds(180));

    assertEquals(MemoryGameState.FINISHED, last(states).state());
    assertEquals(Duration.ZERO, last(states).remainingTime());
    assertTrue(clock.cancelAllCalls > 0);
    int published = states.size();
    clock.runDueCallbacks();
    assertEquals(published, states.size());
  }

  @Test
  void abandonmentStopsCallbacksAndRestartCreatesAttemptTwoWithResetClock() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    orchestrator.abandon();
    int published = states.size();
    clock.advanceTo(Duration.ofSeconds(5));
    assertEquals(published, states.size());

    // A second orchestrator run can restart only after a completed first attempt.
    orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(180));
    orchestrator.restart();
    assertEquals(2, last(states).attempt());
    assertEquals(Duration.ofMinutes(3), last(states).remainingTime());
  }

  @Test
  void restartIsIgnoredUntilTheFirstAttemptFinishes() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();

    orchestrator.restart();

    assertEquals(1, last(states).attempt());
    assertEquals(MemoryGameState.MEMORIZING, last(states).state());
  }

  @Test
  void restartIsIgnoredAfterTheSecondAttemptFinishes() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(180));
    orchestrator.restart();
    assertEquals(2, last(states).attempt());

    clock.advanceTo(Duration.ofSeconds(180));
    orchestrator.restart();

    assertEquals(2, last(states).attempt());
    assertEquals(MemoryGameState.FINISHED, last(states).state());
    assertFalse(last(states).canRestart());
  }

  @Test
  void startAfterFinishBeginsANewCycleAtAttemptOne() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states, POSITION_A, POSITION_B);
    orchestrator.start();
    clock.advanceTo(Duration.ofSeconds(5));
    orchestrator.submitAnswer(answerFrom(last(states).challenge().orElseThrow()));
    clock.advanceTo(Duration.ofSeconds(180));
    assertEquals(MemoryGameState.FINISHED, last(states).state());

    // Leaving to Home and entering the screen again starts a fresh two-attempt cycle.
    orchestrator.abandon();
    orchestrator.start();

    assertEquals(1, last(states).attempt());
    assertEquals(MemoryGameState.MEMORIZING, last(states).state());
    assertEquals(0, last(states).score());
    assertEquals(0, last(states).maxPossibleScore());
    assertEquals(Duration.ofMinutes(3), last(states).remainingTime());
  }

  @Test
  void reportsActionableEmptyStateWithoutStartingAnInvalidSession() {
    FakeClock clock = new FakeClock();
    List<MemoryGameSnapshot> states = new ArrayList<>();
    MemoryGameOrchestrator orchestrator = orchestrator(clock, states);

    orchestrator.start();

    assertEquals(MemoryGameState.READY, last(states).state());
    assertTrue(last(states).emptySource());
    assertTrue(last(states).challenge().isEmpty());
  }

  private static MemoryGameOrchestrator orchestrator(
      FakeClock clock, List<MemoryGameSnapshot> states, String... fens) {
    MemoryGamePosition[] positions = new MemoryGamePosition[fens.length];
    for (int i = 0; i < fens.length; i++) positions[i] = new MemoryGamePosition("game-" + i, fens[i]);
    MemoryGamePositionSelector selector = new MemoryGamePositionSelector(
        () -> List.of(positions), new ChesspressoRulesEngine(), new Random(4));
    MemoryGameOrchestrator orchestrator = new MemoryGameOrchestrator(selector, clock, MemoryGameUiDispatcher.immediate());
    orchestrator.observe(states::add);
    return orchestrator;
  }

  private static Map<Square, MemoryGamePiece> answerFrom(MemoryGameChallenge challenge) {
    Map<Square, MemoryGamePiece> answer = new HashMap<>();
    challenge.hiddenPieces().forEach(piece -> answer.put(piece.square(), piece));
    return answer;
  }

  private static MemoryGameSnapshot last(List<MemoryGameSnapshot> states) {
    return states.getLast();
  }

  private static final class FakeClock implements MemoryGameClock {
    private final List<Scheduled> scheduled = new ArrayList<>();
    private Duration elapsed = Duration.ZERO;
    private int cancelAllCalls;

    @Override public void reset() { elapsed = Duration.ZERO; scheduled.clear(); }
    @Override public Duration elapsed() { return elapsed; }
    @Override public MemoryGameCancellable schedule(Duration delay, Runnable callback) {
      Scheduled entry = new Scheduled(elapsed.plus(delay), callback);
      scheduled.add(entry);
      return () -> entry.cancelled = true;
    }
    @Override public void cancelAll() {
      cancelAllCalls++;
      scheduled.forEach(entry -> entry.cancelled = true);
    }
    void advanceTo(Duration target) {
      // Fire each due callback at its own due instant (like a real clock), so a long
      // jump still runs the feedback -> next round -> memorization chain in order.
      while (true) {
        Scheduled next = null;
        for (Scheduled entry : scheduled) {
          if (!entry.cancelled && entry.when.compareTo(target) <= 0
              && (next == null || entry.when.compareTo(next.when) < 0)) {
            next = entry;
          }
        }
        if (next == null) break;
        next.cancelled = true;
        elapsed = next.when;
        next.callback.run();
      }
      elapsed = target;
    }
    void runDueCallbacks() {
      advanceTo(elapsed);
    }
    private static final class Scheduled {
      private final Duration when;
      private final Runnable callback;
      private boolean cancelled;
      private Scheduled(Duration when, Runnable callback) { this.when = when; this.callback = callback; }
    }
  }
}
