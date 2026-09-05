package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.application.tactics.TacticHint;
import com.escontrela.lastmove.application.tactics.TacticMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.tactics.TacticExerciseReference;
import com.escontrela.lastmove.domain.training.storm.StormGame;
import java.util.Objects;
import java.util.Optional;

/** Session facade that combines reusable tactic solving with Storm accounting. */
public final class StormGamePuzzleRunner implements AutoCloseable {
  private final StormGameExerciseSource source;
  private final ChessGameFactory gameFactory;
  private final StormGame stormGame;
  private StormGameChallenge challenge;
  private StormGamePuzzleAttempt attempt;
  private StormGameFeedback lastFeedback;

  public StormGamePuzzleRunner(
      StormGameExerciseSource source, ChessGameFactory gameFactory, StormGame stormGame) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.stormGame = Objects.requireNonNull(stormGame, "stormGame must not be null");
  }

  public void start(StormGameChallenge challenge) {
    StormGameChallenge required = Objects.requireNonNull(challenge, "challenge must not be null");
    if (stormGame.state() == com.escontrela.lastmove.domain.training.storm.StormGameState.READY)
      stormGame.start();
    TacticExerciseReference reference =
        source.findAllTrainableExercises().stream()
            .filter(candidate -> candidate.ownerId().equals(required.ownerId()))
            .filter(candidate -> candidate.suiteId().equals(required.suiteId()))
            .filter(candidate -> candidate.exercise().id().equals(required.exerciseId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown Storm exercise"));
    stormGame.activatePuzzle(required.exerciseId());
    this.challenge = required;
    attempt =
        StormGamePuzzleAttempt.start(
            required.suiteId(), "Storm", reference.exercise(), gameFactory);
    lastFeedback = null;
  }

  public StormGameMoveOutcome attemptMove(MoveCommand command) {
    requireStarted();
    boolean wrongColor = attempt.isWrongColor(command);
    TacticMoveOutcome outcome = attempt.attemptMove(command);
    if (!outcome.accepted() && !wrongColor) stormGame.markError();
    if (outcome.workspace().solved()) stormGame.completePuzzle();
    return outcome(outcome.workspace(), outcome.accepted(), false, Optional.empty(), Optional.empty());
  }

  public StormGameMoveOutcome requestHint() {
    requireStarted();
    boolean revealTarget = attempt.workspace().hintCount() > 0;
    TacticHint hint = attempt.requestHint();
    if (hint.sourceSquare().isPresent()) stormGame.markHintUsed();
    return outcome(hint.workspace(), false, true, hint.sourceSquare(),
        revealTarget ? attempt.nextMoveTarget() : Optional.empty());
  }

  public StormGameSnapshot snapshot() {
    return snapshot(challenge == null ? Optional.empty() : Optional.of(challenge));
  }

  public void updateElapsedTime(java.time.Duration elapsed) {
    stormGame.updateElapsedTime(elapsed);
  }

  @Override
  public void close() {
    challenge = null;
    attempt = null;
    lastFeedback = null;
  }

  private StormGameMoveOutcome outcome(
      TacticWorkspace workspace,
      boolean correct,
      boolean hint,
      Optional<com.escontrela.lastmove.domain.common.Square> square,
      Optional<com.escontrela.lastmove.domain.common.Square> targetSquare) {
    boolean puzzleFailed = challenge != null
        && stormGame.puzzleProgress(challenge.exerciseId())
            .map(StormGame.PuzzleProgress::failed)
            .orElse(!correct);
    lastFeedback =
        new StormGameFeedback(
            correct, puzzleFailed, hint, square, targetSquare, workspace.solved(), Optional.of(workspace));
    return new StormGameMoveOutcome(snapshot(), lastFeedback);
  }

  private StormGameSnapshot snapshot(Optional<StormGameChallenge> current) {
    return new StormGameSnapshot(
        stormGame.state(),
        stormGame.remainingTime(),
        current,
        stormGame.puzzlesFinished(),
        stormGame.hits(),
        stormGame.errors(),
        stormGame.percentage(),
        false,
        attempt == null ? Optional.empty() : Optional.of(attempt.workspace()),
        Optional.ofNullable(lastFeedback));
  }

  private void requireStarted() {
    if (attempt == null) throw new IllegalStateException("Storm puzzle is not started");
  }
}
