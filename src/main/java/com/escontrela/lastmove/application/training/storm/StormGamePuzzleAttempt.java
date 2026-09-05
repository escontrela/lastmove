package com.escontrela.lastmove.application.training.storm;

import com.escontrela.lastmove.application.tactics.TacticHint;
import com.escontrela.lastmove.application.tactics.TacticMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.tactics.TacticExercise;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reusable, engine-independent mechanics for solving one tactic line. */
public final class StormGamePuzzleAttempt {
  private final TacticSuiteId suiteId;
  private final String suiteTitle;
  private final TacticExercise exercise;
  private final ChessGameFactory gameFactory;
  private AnalysisNode current;
  private List<AnalysisNode> expected;
  private com.escontrela.lastmove.domain.game.PositionSnapshot position;
  private boolean solved;
  private int attemptedMoves;
  private int correctMoves;
  private int hintCount;

  private StormGamePuzzleAttempt(TacticSuiteId suiteId, String suiteTitle, TacticExercise exercise,
      ChessGameFactory gameFactory) {
    this.suiteId = Objects.requireNonNull(suiteId, "suiteId must not be null");
    this.suiteTitle = Objects.requireNonNull(suiteTitle, "suiteTitle must not be null");
    this.exercise = Objects.requireNonNull(exercise, "exercise must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.expected = exercise.solution().tree().roots();
    this.position = exercise.solution().initialPosition();
  }

  public static StormGamePuzzleAttempt start(TacticSuiteId suiteId, String suiteTitle,
      TacticExercise exercise, ChessGameFactory gameFactory) {
    return new StormGamePuzzleAttempt(suiteId, suiteTitle, exercise, gameFactory);
  }

  public TacticMoveOutcome attemptMove(MoveCommand command) {
    MoveCommand required = Objects.requireNonNull(command, "command must not be null");
    ChessGame game = gameFactory.createAnalysisGame(position);
    MoveExecutionResult result = game.move(required);
    attemptedMoves++;
    if (!result.accepted()) return new TacticMoveOutcome(workspace("That move is not legal."), false);
    Optional<MoveDescriptor> actual = result.move();
    boolean accepted = actual.isPresent() && expected.stream()
        .anyMatch(node -> sameMove(node.ply().move(), actual.orElseThrow()));
    if (!accepted) return new TacticMoveOutcome(workspace("That move does not solve the tactic. Try again."), false);
    correctMoves++;
    current = expected.stream().filter(node -> sameMove(node.ply().move(), actual.orElseThrow())).findFirst().orElseThrow();
    position = result.newSnapshot();
    advanceAutomaticReplies();
    return new TacticMoveOutcome(workspace(), true);
  }

  /** Returns whether the move starts with a piece that is not allowed to move yet. */
  public boolean isWrongColor(MoveCommand command) {
    MoveCommand required = Objects.requireNonNull(command, "command must not be null");
    return position.pieces().stream()
        .filter(piece -> piece.square().equals(required.from()))
        .findFirst()
        .map(piece -> piece.color() != position.activeColor())
        .orElse(false);
  }

  public TacticHint requestHint() {
    Optional<Square> source = solved || expected.isEmpty() ? Optional.empty()
        : Optional.of(expected.getFirst().ply().move().from());
    if (source.isPresent()) hintCount++;
    return new TacticHint(workspace(source.isPresent() ? "Hint used: move the highlighted piece."
        : "No hint is available for this tactic."), source);
  }

  /** Returns the destination of the currently expected solver move without applying it. */
  public Optional<Square> nextMoveTarget() {
    return solved || expected.isEmpty()
        ? Optional.empty()
        : Optional.of(expected.getFirst().ply().move().to());
  }

  public TacticWorkspace workspace() { return workspace(solved ? "Solved!" : "Find the best move for " + colorName(exercise.solverColor()) + "."); }
  public boolean solved() { return solved; }

  private void advanceAutomaticReplies() {
    while (current != null) {
      List<AnalysisNode> children = exercise.solution().tree().children(current.id());
      if (children.isEmpty()) { solved = true; expected = List.of(); return; }
      if (children.getFirst().ply().movingColor() == exercise.solverColor()) { expected = children; return; }
      current = children.getFirst();
      position = current.ply().resultingPosition();
    }
  }

  private TacticWorkspace workspace(String status) {
    return new TacticWorkspace(suiteId, suiteTitle, exercise.id(), exercise.title(), exercise.solverColor(),
        position, exercise.hasSolution(), solved, attemptedMoves, correctMoves, hintCount,
        accuracyPercentage(), solved ? status + " Accuracy: " + accuracyPercentage() + "% (" + correctMoves + "/" + attemptedMoves + ")" : status);
  }

  private static boolean sameMove(MoveDescriptor expected, MoveDescriptor actual) {
    return expected.from().equals(actual.from()) && expected.to().equals(actual.to()) && expected.promotion().equals(actual.promotion());
  }
  private static String colorName(PieceColor color) { return color == PieceColor.WHITE ? "White" : "Black"; }
  private int accuracyPercentage() { return Math.max(0, attemptedMoves == 0 ? 100 : (int) Math.round(correctMoves * 100.0 / attemptedMoves) - hintCount * 30); }
}
