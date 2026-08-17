package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.application.tactics.AppendTacticSolutionMoveCommand;
import com.escontrela.lastmove.application.tactics.CopyAnalysisSessionTacticCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticExerciseFromFenCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.DeleteTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.DeleteTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.MoveTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.MoveTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.RenameTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.RenameTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.TacticAuthoringMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticExerciseSummary;
import com.escontrela.lastmove.application.tactics.TacticHint;
import com.escontrela.lastmove.application.tactics.TacticMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticSuiteDetails;
import com.escontrela.lastmove.application.tactics.TacticSuiteSummary;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExercise;
import com.escontrela.lastmove.domain.tactics.TacticExerciseFactory;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticRepository;
import com.escontrela.lastmove.domain.tactics.TacticSuite;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Coordinates persisted tactic suites and the process-local state of a training attempt. */
@Service
public final class TacticService {

  private final TacticRepository tacticRepository;
  private final AnalysisSessionRepository analysisSessionRepository;
  private final TacticExerciseFactory exerciseFactory;
  private final ChessGameFactory gameFactory;
  private final PersistenceAvailability availability;
  private final Map<AttemptKey, Attempt> attempts = new HashMap<>();

  public TacticService(
      TacticRepository tacticRepository,
      AnalysisSessionRepository analysisSessionRepository,
      TacticExerciseFactory exerciseFactory,
      ChessGameFactory gameFactory,
      PersistenceAvailability availability) {
    this.tacticRepository =
        Objects.requireNonNull(tacticRepository, "tacticRepository must not be null");
    this.analysisSessionRepository =
        Objects.requireNonNull(
            analysisSessionRepository, "analysisSessionRepository must not be null");
    this.exerciseFactory =
        Objects.requireNonNull(exerciseFactory, "exerciseFactory must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.availability = Objects.requireNonNull(availability, "availability must not be null");
  }

  public TacticSuiteSummary createSuite(CreateTacticSuiteCommand command) {
    assertAvailable();
    CreateTacticSuiteCommand required = Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = TacticSuite.create(required.ownerId(), required.title());
    suite.setDescription(required.description());
    return summary(tacticRepository.save(suite));
  }

  public List<TacticSuiteSummary> listSuites(PlayerId ownerId) {
    assertAvailable();
    return tacticRepository
        .findAllByOwner(Objects.requireNonNull(ownerId, "ownerId must not be null"))
        .stream()
        .map(this::summary)
        .toList();
  }

  public TacticSuiteDetails suiteDetails(PlayerId ownerId, TacticSuiteId suiteId) {
    assertAvailable();
    TacticSuite suite = ownedSuite(ownerId, suiteId);
    return new TacticSuiteDetails(
        summary(suite), suite.exercises().stream().map(this::summary).toList());
  }

  /** Renames one owned tactic suite. */
  public TacticSuiteSummary renameSuite(RenameTacticSuiteCommand command) {
    assertAvailable();
    RenameTacticSuiteCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    suite.rename(required.title());
    return summary(tacticRepository.save(suite));
  }

  /** Moves one owned tactic suite one place in the owner's library order. */
  public boolean moveSuite(MoveTacticSuiteCommand command) {
    assertAvailable();
    MoveTacticSuiteCommand required =
        Objects.requireNonNull(command, "command must not be null");
    List<TacticSuite> ordered = tacticRepository.findAllByOwner(required.ownerId());
    int currentIndex = indexOf(ordered, required.suiteId());
    if (currentIndex < 0) {
      throw unknownSuite(required.suiteId());
    }
    int targetIndex = currentIndex + required.offset();
    if (targetIndex < 0 || targetIndex >= ordered.size()) {
      return false;
    }
    return tacticRepository.moveSuiteToIndex(required.ownerId(), required.suiteId(), targetIndex);
  }

  /** Deletes one owned tactic suite and all of its exercises. */
  public void deleteSuite(DeleteTacticSuiteCommand command) {
    assertAvailable();
    DeleteTacticSuiteCommand required =
        Objects.requireNonNull(command, "command must not be null");
    if (!tacticRepository.deleteByIdAndOwner(required.suiteId(), required.ownerId())) {
      throw unknownSuite(required.suiteId());
    }
    attempts
        .keySet()
        .removeIf(
            key ->
                key.suiteId().equals(required.suiteId())
                    && key.ownerId().equals(required.ownerId()));
  }

  /** Adds an empty exercise from a FEN; the solution is authored in a later UI step. */
  public TacticExerciseSummary createExerciseFromFen(CreateTacticExerciseFromFenCommand command) {
    assertAvailable();
    CreateTacticExerciseFromFenCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    TacticExercise exercise =
        exerciseFactory.empty(
            required.title(), gameFactory.createAnalysisGame(required.fen()).currentPosition());
    suite.addExercise(exercise);
    tacticRepository.save(suite);
    return summary(exercise);
  }

  /** Imports the current analysis position and its next-move continuations as a new exercise. */
  public TacticExerciseSummary copyAnalysisSessionTactic(CopyAnalysisSessionTacticCommand command) {
    assertAvailable();
    CopyAnalysisSessionTacticCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    var session =
        analysisSessionRepository
            .findById(required.sessionId())
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Unknown analysis session " + required.sessionId().value()));
    TacticExercise exercise = exerciseFactory.fromDocument(required.title(), session.document());
    suite.addExercise(exercise);
    tacticRepository.save(suite);
    return summary(exercise);
  }

  public TacticExerciseSummary renameExercise(RenameTacticExerciseCommand command) {
    assertAvailable();
    RenameTacticExerciseCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    TacticExercise exercise = exercise(suite, required.exerciseId());
    exercise.rename(required.title());
    suite.touch();
    tacticRepository.save(suite);
    return summary(exercise);
  }

  public boolean moveExercise(MoveTacticExerciseCommand command) {
    assertAvailable();
    MoveTacticExerciseCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    boolean moved = suite.moveExercise(required.exerciseId(), required.offset());
    if (moved) tacticRepository.save(suite);
    return moved;
  }

  public void deleteExercise(DeleteTacticExerciseCommand command) {
    assertAvailable();
    DeleteTacticExerciseCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    if (!suite.removeExercise(required.exerciseId())) {
      throw new NoSuchElementException("Unknown tactic exercise " + required.exerciseId().value());
    }
    attempts.remove(new AttemptKey(required.ownerId(), required.suiteId(), required.exerciseId()));
    tacticRepository.save(suite);
  }

  /** Starts a fresh attempt, returning the initial position rather than a mutable domain object. */
  public TacticWorkspace startExercise(
      PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId) {
    assertAvailable();
    TacticSuite suite = ownedSuite(ownerId, suiteId);
    TacticExercise exercise = exercise(suite, exerciseId);
    Attempt attempt = Attempt.start(suite, exercise);
    attempts.put(new AttemptKey(ownerId, suiteId, exerciseId), attempt);
    return attempt.workspace();
  }

  /** Checks a legal move against the current expected solution continuations. */
  public TacticMoveOutcome attemptMove(
      PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId, MoveCommand command) {
    assertAvailable();
    Attempt attempt = attempts.get(new AttemptKey(ownerId, suiteId, exerciseId));
    if (attempt == null) {
      return new TacticMoveOutcome(startExercise(ownerId, suiteId, exerciseId), false);
    }
    ChessGame game = gameFactory.createAnalysisGame(attempt.position);
    MoveExecutionResult result =
        game.move(Objects.requireNonNull(command, "command must not be null"));
    if (!result.accepted()) {
      return new TacticMoveOutcome(attempt.workspace("That move is not legal."), false);
    }
    attempt.recordAttempt();
    if (!attempt.accept(result)) {
      return new TacticMoveOutcome(
          attempt.workspace("That move does not solve the tactic. Try again."), false);
    }
    return new TacticMoveOutcome(attempt.workspace(), true);
  }

  /** Reveals the source square of the next expected move and applies the hint score penalty. */
  public TacticHint requestHint(
      PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId) {
    assertAvailable();
    AttemptKey key = new AttemptKey(ownerId, suiteId, exerciseId);
    Attempt attempt = attempts.get(key);
    if (attempt == null) {
      startExercise(ownerId, suiteId, exerciseId);
      attempt = attempts.get(key);
    }
    Optional<Square> sourceSquare = attempt.requestHint();
    return new TacticHint(
        attempt.workspace(
            sourceSquare.isPresent()
                ? "Hint used: move the highlighted piece. Each hint costs 30 percentage points."
                : "No hint is available for this tactic."),
        sourceSquare);
  }

  /** Appends a legal move to one solution branch and persists the changed exercise immediately. */
  public TacticAuthoringMoveOutcome appendSolutionMove(AppendTacticSolutionMoveCommand command) {
    assertAvailable();
    AppendTacticSolutionMoveCommand required =
        Objects.requireNonNull(command, "command must not be null");
    TacticSuite suite = ownedSuite(required.ownerId(), required.suiteId());
    TacticExercise exercise = exercise(suite, required.exerciseId());
    var basePosition =
        required
            .parentNodeId()
            .map(id -> exercise.solution().tree().node(id).ply().resultingPosition())
            .orElse(exercise.solution().initialPosition());
    ChessGame game = gameFactory.createAnalysisGame(basePosition);
    MoveExecutionResult result = game.move(required.move());
    if (!result.accepted()) {
      return new TacticAuthoringMoveOutcome(
          workspace(suite, exercise, basePosition, false, "That move is not legal."),
          Optional.empty(),
          false);
    }
    AnalysisNode node =
        required.parentNodeId().isEmpty()
            ? exercise
                .solution()
                .tree()
                .addRoot(
                    new Ply(
                        UUID.randomUUID(),
                        result.move().orElseThrow(),
                        result.newSnapshot(),
                        basePosition.fullmoveNumber(),
                        basePosition.activeColor()))
            : exercise
                .solution()
                .tree()
                .addChild(
                    required.parentNodeId().orElseThrow(),
                    new Ply(
                        UUID.randomUUID(),
                        result.move().orElseThrow(),
                        result.newSnapshot(),
                        basePosition.fullmoveNumber(),
                        basePosition.activeColor()));
    exercise.touch();
    suite.touch();
    tacticRepository.save(suite);
    return new TacticAuthoringMoveOutcome(
        workspace(suite, exercise, result.newSnapshot(), false, "Solution move added."),
        Optional.of(node.id()),
        true);
  }

  private TacticSuite ownedSuite(PlayerId ownerId, TacticSuiteId suiteId) {
    return tacticRepository
        .findByIdAndOwner(
            Objects.requireNonNull(suiteId, "suiteId must not be null"),
            Objects.requireNonNull(ownerId, "ownerId must not be null"))
        .orElseThrow(() -> new NoSuchElementException("Unknown tactic suite " + suiteId.value()));
  }

  private NoSuchElementException unknownSuite(TacticSuiteId suiteId) {
    return new NoSuchElementException("Unknown tactic suite " + suiteId.value());
  }

  private int indexOf(List<TacticSuite> ordered, TacticSuiteId suiteId) {
    for (int index = 0; index < ordered.size(); index++) {
      if (ordered.get(index).id().equals(suiteId)) {
        return index;
      }
    }
    return -1;
  }

  private TacticExercise exercise(TacticSuite suite, TacticExerciseId exerciseId) {
    return suite
        .exercise(Objects.requireNonNull(exerciseId, "exerciseId must not be null"))
        .orElseThrow(
            () -> new NoSuchElementException("Unknown tactic exercise " + exerciseId.value()));
  }

  private TacticSuiteSummary summary(TacticSuite suite) {
    return new TacticSuiteSummary(
        suite.id(),
        suite.title(),
        suite.description(),
        suite.exercises().size(),
        suite.updatedAt());
  }

  private TacticExerciseSummary summary(TacticExercise exercise) {
    return new TacticExerciseSummary(
        exercise.id(), exercise.title(), exercise.solverColor(), exercise.hasSolution());
  }

  private TacticWorkspace workspace(
      TacticSuite suite,
      TacticExercise exercise,
      com.escontrela.lastmove.domain.game.PositionSnapshot position,
      boolean solved,
      String status) {
    return new TacticWorkspace(
        suite.id(),
        suite.title(),
        exercise.id(),
        exercise.title(),
        exercise.solverColor(),
        position,
        exercise.hasSolution(),
        solved,
        0,
        0,
        0,
        0,
        status);
  }

  private void assertAvailable() {
    if (!availability.isAvailable()) {
      throw new PersistenceUnavailableException("Tactic persistence is unavailable");
    }
  }

  private record AttemptKey(PlayerId ownerId, TacticSuiteId suiteId, TacticExerciseId exerciseId) {}

  private static final class Attempt {
    private final TacticSuiteId suiteId;
    private final String suiteTitle;
    private final TacticExercise exercise;
    private AnalysisNode current;
    private List<AnalysisNode> expected;
    private com.escontrela.lastmove.domain.game.PositionSnapshot position;
    private boolean solved;
    private int attemptedMoves;
    private int correctMoves;
    private int hintCount;

    private Attempt(TacticSuite suite, TacticExercise exercise) {
      this.suiteId = suite.id();
      this.suiteTitle = suite.title();
      this.exercise = exercise;
      this.expected = exercise.solution().tree().roots();
      this.position = exercise.solution().initialPosition();
    }

    static Attempt start(TacticSuite suite, TacticExercise exercise) {
      return new Attempt(suite, exercise);
    }

    boolean accept(MoveExecutionResult result) {
      List<AnalysisNode> matches =
          expected.stream()
              .filter(node -> sameMove(node.ply().move(), result.move().orElseThrow()))
              .toList();
      if (matches.isEmpty()) return false;
      correctMoves++;
      current = matches.getFirst();
      position = result.newSnapshot();
      advanceAutomaticReplies();
      return true;
    }

    void recordAttempt() {
      attemptedMoves++;
    }

    Optional<Square> requestHint() {
      if (solved || expected.isEmpty()) return Optional.empty();
      hintCount++;
      return Optional.of(expected.getFirst().ply().move().from());
    }

    private void advanceAutomaticReplies() {
      while (current != null) {
        List<AnalysisNode> children = exercise.solution().tree().children(current.id());
        if (children.isEmpty()) {
          solved = true;
          expected = List.of();
          return;
        }
        if (children.getFirst().ply().movingColor() == exercise.solverColor()) {
          expected = children;
          return;
        }
        current = children.getFirst();
        position = current.ply().resultingPosition();
      }
    }

    TacticWorkspace workspace() {
      if (!exercise.hasSolution())
        return workspace("This tactic needs a solution line before it can be trained.");
      return workspace(
          solved ? "Solved!" : "Find the best move for " + colorName(exercise.solverColor()) + ".");
    }

    TacticWorkspace workspace(String status) {
      return new TacticWorkspace(
          suiteId,
          suiteTitle,
          exercise.id(),
          exercise.title(),
          exercise.solverColor(),
          position,
          exercise.hasSolution(),
          solved,
          attemptedMoves,
          correctMoves,
          hintCount,
          accuracyPercentage(),
          solved
              ? status
                  + " Accuracy: "
                  + accuracyPercentage()
                  + "% ("
                  + correctMoves
                  + "/"
                  + attemptedMoves
                  + ")"
              : status);
    }

    private static boolean sameMove(MoveDescriptor expected, MoveDescriptor actual) {
      return expected.from().equals(actual.from())
          && expected.to().equals(actual.to())
          && expected.promotion().equals(actual.promotion());
    }

    private static String colorName(PieceColor color) {
      return color == PieceColor.WHITE ? "White" : "Black";
    }

    private int accuracyPercentage() {
      int rawAccuracy =
          attemptedMoves == 0 ? 100 : (int) Math.round((correctMoves * 100.0) / attemptedMoves);
      return Math.max(0, rawAccuracy - (hintCount * 30));
    }
  }
}
