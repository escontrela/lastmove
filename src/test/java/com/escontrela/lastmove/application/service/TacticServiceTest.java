package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.tactics.AppendTacticSolutionMoveCommand;
import com.escontrela.lastmove.application.tactics.CopyAnalysisSessionTacticCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticExerciseFromFenCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.DeleteTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.MoveTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.RenameTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.TacticExerciseSummary;
import com.escontrela.lastmove.application.tactics.TacticSuiteSummary;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseFactory;
import com.escontrela.lastmove.domain.tactics.TacticRepository;
import com.escontrela.lastmove.domain.tactics.TacticSuite;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.session.InMemoryAnalysisSessionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticServiceTest {

  private final PlayerId owner = PlayerId.of(1L);
  private final InMemoryAnalysisSessionRepository analysisSessions =
      new InMemoryAnalysisSessionRepository();
  private final AnalysisDocumentFactory documentFactory = new AnalysisDocumentFactory();
  private final ChessGameFactory gameFactory = new ChessGameFactory(new ChesspressoRulesEngine());
  private final TacticService service =
      new TacticService(
          new FakeTacticRepository(),
          analysisSessions,
          new TacticExerciseFactory(documentFactory),
          gameFactory,
          PersistenceAvailability.available());

  @Test
  void authorsAndSolvesAForcedLineWhileAutoPlayingTheReply() {
    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Openings", Optional.empty()));
    TacticExerciseSummary exercise =
        service.createExerciseFromFen(
            new CreateTacticExerciseFromFenCommand(
                owner,
                suite.suiteId(),
                "King pawn",
                Fen.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")));

    var first =
        service.appendSolutionMove(
            new AppendTacticSolutionMoveCommand(
                owner,
                suite.suiteId(),
                exercise.exerciseId(),
                Optional.empty(),
                move("e2", "e4")));
    assertTrue(first.accepted());
    var second =
        service.appendSolutionMove(
            new AppendTacticSolutionMoveCommand(
                owner,
                suite.suiteId(),
                exercise.exerciseId(),
                first.nodeId(),
                move("e7", "e5")));
    assertTrue(second.accepted());

    assertTrue(service.startExercise(owner, suite.suiteId(), exercise.exerciseId()).readyToSolve());
    var wrong = service.attemptMove(owner, suite.suiteId(), exercise.exerciseId(), move("d2", "d4"));
    assertFalse(wrong.accepted());
    var outcome = service.attemptMove(owner, suite.suiteId(), exercise.exerciseId(), move("e2", "e4"));

    assertTrue(outcome.accepted());
    assertTrue(outcome.workspace().solved());
    assertEquals("e5", outcome.workspace().position().lastMove().orElseThrow().san().getValue());
    assertEquals(50, outcome.workspace().accuracyPercentage());
  }

  @Test
  void runsAnAnalysisTreeAsAnUnpersistedTemporaryTactic() {
    var game =
        gameFactory.createAnalysisGame(
            Fen.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
    var document = documentFactory.fromPosition(game.currentPosition(), Optional.empty());
    document.apply(game.move(move("e2", "e4")));
    document.apply(game.move(move("e7", "e5")));
    assertTrue(document.previous());
    var alternative = gameFactory.createAnalysisGame(document.currentPosition());
    document.apply(alternative.move(move("c7", "c5")));

    var temporary = service.startTemporaryExercise("Study chapter", document);
    assertEquals("Study chapter", temporary.workspace().exerciseTitle());
    assertTrue(temporary.workspace().readyToSolve());
    assertTrue(service.listSuites(owner).isEmpty());

    var outcome = service.attemptTemporaryMove(temporary.sessionId(), move("e2", "e4"));
    assertTrue(outcome.accepted());
    assertTrue(outcome.workspace().solved());
    assertEquals("c5", outcome.workspace().position().lastMove().orElseThrow().san().getValue());

    service.closeTemporaryExercise(temporary.sessionId());
    assertThrows(
        NoSuchElementException.class,
        () -> service.restartTemporaryExercise(temporary.sessionId()));
  }

  @Test
  void exposesBlackAsTheSolverWhenTheFenIsBlackToMove() {
    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Endgames", Optional.empty()));
    TacticExerciseSummary exercise =
        service.createExerciseFromFen(
            new CreateTacticExerciseFromFenCommand(
                owner, suite.suiteId(), "Black move", Fen.of("8/8/8/8/8/8/8/K6k b - - 0 1")));

    assertEquals(PieceColor.BLACK, exercise.solverColor());
    assertFalse(exercise.readyToSolve());
  }

  @Test
  void importsTheAnalysisLineAsTheExerciseSolution() {
    var game =
        gameFactory.createAnalysisGame(
            Fen.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
    var document = documentFactory.fromPosition(game.currentPosition(), Optional.empty());
    var e4 = game.move(move("e2", "e4"));
    document.apply(e4);
    document.apply(game.move(move("e7", "e5")));
    AnalysisSessionId sessionId = AnalysisSessionId.random();
    analysisSessions.save(new AnalysisSession(sessionId, "Imported line", AnalysisOrigin.INITIAL_POSITION, document));

    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Imported", Optional.empty()));
    TacticExerciseSummary exercise =
        service.copyAnalysisSessionTactic(
            new CopyAnalysisSessionTacticCommand(owner, suite.suiteId(), sessionId, "King pawn"));

    assertTrue(exercise.readyToSolve());
    service.startExercise(owner, suite.suiteId(), exercise.exerciseId());
    var outcome = service.attemptMove(owner, suite.suiteId(), exercise.exerciseId(), move("e2", "e4"));

    assertTrue(outcome.accepted());
    assertTrue(outcome.workspace().solved());
    assertEquals("e5", outcome.workspace().position().lastMove().orElseThrow().san().getValue());
  }

  @Test
  void reducesTheFinalScoreByThirtyPointsForEachHint() {
    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Hints", Optional.empty()));
    TacticExerciseSummary exercise =
        service.createExerciseFromFen(
            new CreateTacticExerciseFromFenCommand(
                owner,
                suite.suiteId(),
                "Hinted move",
                Fen.of("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")));
    var authored =
        service.appendSolutionMove(
            new AppendTacticSolutionMoveCommand(
                owner, suite.suiteId(), exercise.exerciseId(), Optional.empty(), move("e2", "e4")));

    service.startExercise(owner, suite.suiteId(), exercise.exerciseId());
    var hint = service.requestHint(owner, suite.suiteId(), exercise.exerciseId());
    var result = service.attemptMove(owner, suite.suiteId(), exercise.exerciseId(), move("e2", "e4"));

    assertEquals(Optional.of(Square.of("e2")), hint.sourceSquare());
    assertTrue(authored.accepted());
    assertTrue(result.workspace().solved());
    assertEquals(70, result.workspace().accuracyPercentage());
    assertEquals(1, result.workspace().hintCount());
  }

  @Test
  void renamesASuiteAndPersistsTheNewTitle() {
    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Forks", Optional.empty()));

    service.renameSuite(new RenameTacticSuiteCommand(owner, suite.suiteId(), "Pins"));

    assertEquals(
        List.of("Pins"), service.listSuites(owner).stream().map(TacticSuiteSummary::title).toList());
  }

  @Test
  void movesASuiteOnePlaceAndReportsWhenAlreadyAtTheEdge() {
    TacticSuiteSummary first =
        service.createSuite(new CreateTacticSuiteCommand(owner, "First", Optional.empty()));
    service.createSuite(new CreateTacticSuiteCommand(owner, "Second", Optional.empty()));

    assertTrue(service.moveSuite(new MoveTacticSuiteCommand(owner, first.suiteId(), 1)));
    assertEquals(
        List.of("Second", "First"),
        service.listSuites(owner).stream().map(TacticSuiteSummary::title).toList());
    assertFalse(service.moveSuite(new MoveTacticSuiteCommand(owner, first.suiteId(), 1)));
  }

  @Test
  void deletesASuiteAndRejectsAnUnknownSuite() {
    TacticSuiteSummary suite =
        service.createSuite(new CreateTacticSuiteCommand(owner, "Doomed", Optional.empty()));

    service.deleteSuite(new DeleteTacticSuiteCommand(owner, suite.suiteId()));

    assertTrue(service.listSuites(owner).isEmpty());
    assertThrows(
        NoSuchElementException.class,
        () -> service.deleteSuite(new DeleteTacticSuiteCommand(owner, suite.suiteId())));
  }

  private MoveCommand move(String from, String to) {
    return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
  }

  private static final class FakeTacticRepository implements TacticRepository {
    private final Map<TacticSuiteId, TacticSuite> suites = new HashMap<>();
    private final Map<PlayerId, List<TacticSuiteId>> order = new HashMap<>();

    @Override
    public TacticSuite save(TacticSuite suite) {
      suites.put(suite.id(), suite);
      order.computeIfAbsent(suite.ownerId(), ignored -> new ArrayList<>());
      if (!order.get(suite.ownerId()).contains(suite.id())) order.get(suite.ownerId()).add(suite.id());
      return suite;
    }

    @Override
    public Optional<TacticSuite> findByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId) {
      return Optional.ofNullable(suites.get(suiteId)).filter(suite -> suite.ownerId().equals(ownerId));
    }

    @Override
    public List<TacticSuite> findAllByOwner(PlayerId ownerId) {
      return order.getOrDefault(ownerId, List.of()).stream().map(suites::get).toList();
    }

    @Override
    public boolean deleteByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId) {
      return findByIdAndOwner(suiteId, ownerId).map(suite -> {
        suites.remove(suiteId);
        order.getOrDefault(ownerId, List.of()).remove(suiteId);
        return true;
      }).orElse(false);
    }

    @Override
    public void deleteByOwner(PlayerId ownerId) {
      findAllByOwner(ownerId).forEach(suite -> suites.remove(suite.id()));
      order.remove(ownerId);
    }

    @Override
    public boolean moveSuiteToIndex(PlayerId ownerId, TacticSuiteId suiteId, int targetIndex) {
      List<TacticSuiteId> ordered = order.getOrDefault(ownerId, List.of());
      int currentIndex = ordered.indexOf(suiteId);
      if (currentIndex < 0 || targetIndex < 0 || targetIndex >= ordered.size()) return false;
      ordered.remove(currentIndex);
      ordered.add(targetIndex, suiteId);
      return true;
    }
  }
}
