package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.GamePlayer;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import com.escontrela.lastmove.infrastructure.session.InMemoryAnalysisSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisSessionServiceTest {

  private final AnalysisSessionService service =
      new AnalysisSessionService(
          new InMemoryAnalysisSessionRepository(),
          new ChessGameFactory(new ChesspressoRulesEngine()),
          new AnalysisSessionFactory(),
          new FenService());

  @Test
  void createsAndListsIndependentInitialAndFenSessions() {
    AnalysisSessionSummary initial = service.createInitialSession();
    AnalysisSessionSummary fen =
        service.createFenSession(Fen.of("8/8/8/8/8/8/8/K6k b - - 7 42"));

    assertEquals(AnalysisOrigin.INITIAL_POSITION, initial.origin());
    assertEquals(AnalysisOrigin.FEN, fen.origin());
    assertEquals(2, service.listSessions().size());
    assertEquals(fen.sessionId(), service.listSessions().getFirst().sessionId());
    assertEquals(PieceColor.WHITE, service.currentPosition(initial.sessionId()).activeColor());
    assertEquals(PieceColor.BLACK, service.currentPosition(fen.sessionId()).activeColor());

    AnalysisSessionSummary renamed = service.renameSession(initial.sessionId(), "Opening draft");
    assertEquals("Opening draft", renamed.title());
    assertEquals(
        "Opening draft",
        service.sessionSummary(initial.sessionId()).title());
  }

  @Test
  void deletesOneSessionWithoutChangingTheOthers() {
    AnalysisSessionSummary retained = service.createInitialSession();
    AnalysisSessionSummary deleted = service.createFenSession(Fen.startingPosition());

    assertEquals(deleted, service.deleteSession(deleted.sessionId()));
    assertEquals(List.of(retained), service.listSessions());
    assertThrows(
        java.util.NoSuchElementException.class,
        () -> service.sessionSummary(deleted.sessionId()));
  }

  @Test
  void movesSessionsUpAndDownInTheirVisibleOrder() {
    AnalysisSessionSummary first = service.createInitialSession();
    AnalysisSessionSummary second = service.createInitialSession();
    AnalysisSessionSummary third = service.createInitialSession();

    assertTrue(service.moveSessionDown(third.sessionId()));
    assertEquals(
        List.of(second.sessionId(), third.sessionId(), first.sessionId()),
        service.listSessions().stream().map(AnalysisSessionSummary::sessionId).toList());
    assertTrue(service.moveSessionUp(first.sessionId()));
    assertEquals(
        List.of(second.sessionId(), first.sessionId(), third.sessionId()),
        service.listSessions().stream().map(AnalysisSessionSummary::sessionId).toList());
    assertFalse(service.moveSessionUp(second.sessionId()));
  }

  @Test
  void createsPgnSessionAtItsDeclaredStartingFen() {
    PgnGame game =
        new PgnGame(
            Map.of("White", "Ada", "Black", "Grace", "Event", "Test"),
            "",
            GameResult.UNKNOWN,
            Fen.of("8/8/8/8/8/8/8/K6k b - - 7 42"));

    AnalysisSessionSummary session = service.createPgnSession(game);

    assertEquals(AnalysisOrigin.PGN, session.origin());
    assertEquals("Ada vs. Grace – Test", session.title());
    assertEquals(PieceColor.BLACK, session.currentPosition().activeColor());
    assertEquals(42, session.currentPosition().fullmoveNumber());
  }

  @Test
  void importsTheCompletePgnLineBeforeNavigation() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst(
                "[Event \"Main line\"]\n\n1. e4 e5 2. Nf3 Nc6 *");

    AnalysisSessionSummary session = service.createPgnSession(imported);

    assertEquals(
        List.of("e4", "e5", "Nf3", "Nc6"),
        service.notationLine(session.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());

    var visibleNodes = service.notationNodes(session.sessionId());
    assertEquals(4, visibleNodes.size());
    service.select(session.sessionId(), visibleNodes.get(2).nodeId());
    assertEquals("Nf3", service.moveHistory(session.sessionId()).getLast().move().san().getValue());
  }

  @Test
  void createsUpdatesAndClearsMoveComments() {
    AnalysisSessionSummary session = service.createInitialSession();
    service.attemptMove(session.sessionId(), new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()));
    var nodeId = service.notationTree(session.sessionId()).currentNodeId().orElseThrow();

    service.saveMoveComment(session.sessionId(), nodeId, "  Main opening idea  ");
    assertEquals("Main opening idea", service.moveComment(session.sessionId(), nodeId).orElseThrow());

    service.saveMoveComment(session.sessionId(), nodeId, "  ");
    assertTrue(service.moveComment(session.sessionId(), nodeId).isEmpty());
  }

  @Test
  void exposesImportedVariationsAsSelectableNodes() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst(
                "[Event \"Variation\"]\n\n1. e4 e5 2. Nf3 (2. Bc4) *");

    AnalysisSessionSummary session = service.createPgnSession(imported);
    var e4 = service.rootVariations(session.sessionId()).getFirst();
    var e5 = service.continuations(session.sessionId(), e4.nodeId()).getFirst();
    var alternatives = service.continuations(session.sessionId(), e5.nodeId());
    assertEquals(
        List.of("Nf3", "Bc4"),
        alternatives.stream().map(node -> node.ply().move().san().getValue()).toList());

    var notationTree = service.notationTree(session.sessionId());
    var projectedAlternatives =
        notationTree.roots().getFirst().continuations().getFirst().continuations();
    assertEquals(
        List.of("Nf3", "Bc4"),
        projectedAlternatives.stream().map(node -> node.ply().move().san().getValue()).toList());
    assertTrue(projectedAlternatives.getFirst().mainContinuation());
    assertTrue(projectedAlternatives.getFirst().activeLine());
    assertFalse(projectedAlternatives.getLast().mainContinuation());
    assertFalse(projectedAlternatives.getLast().activeLine());

    service.select(session.sessionId(), alternatives.get(1).nodeId());
    assertEquals("Bc4", service.moveHistory(session.sessionId()).getLast().move().san().getValue());
  }

  @Test
  void appliesMovesAndNavigatesOnlyWithinTheSelectedSession() {
    AnalysisSessionSummary session = service.createInitialSession();

    assertTrue(
        service
            .attemptMove(session.sessionId(), move("e2", "e4"))
            .accepted());
    assertEquals(PieceColor.BLACK, service.currentPosition(session.sessionId()).activeColor());
    assertEquals(
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
        service.currentFen(session.sessionId()));

    service.previous(session.sessionId());
    assertEquals(PieceColor.WHITE, service.currentPosition(session.sessionId()).activeColor());

    service.next(session.sessionId());
    assertEquals(PieceColor.BLACK, service.currentPosition(session.sessionId()).activeColor());
  }

  @Test
  void playsAFictitiousGameThroughChessGameAndExposesItsState() {
    AnalysisSessionSummary game = service.createInitialSession();

    verifyGameState(game.sessionId(), PieceColor.WHITE, false, false, false, false);
    assertMove(game.sessionId(), "e2", "e4", "e4");
    verifyGameState(game.sessionId(), PieceColor.BLACK, false, false, false, false);
    assertMove(game.sessionId(), "e7", "e5", "e5");
    verifyGameState(game.sessionId(), PieceColor.WHITE, false, false, false, false);
    assertMove(game.sessionId(), "g1", "f3", "Nf3");
    verifyGameState(game.sessionId(), PieceColor.BLACK, false, false, false, false);
    assertMove(game.sessionId(), "b8", "c6", "Nc6");
    verifyGameState(game.sessionId(), PieceColor.WHITE, false, false, false, false);

    assertEquals(
        List.of("e4", "e5", "Nf3", "Nc6"),
        service.moveHistory(game.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());
  }

  @Test
  void convertsAPlayedGameIntoAnIndependentRetainedAnalysisSession() {
    ChessGame sourceGame =
        new ChessGameFactory(new ChesspressoRulesEngine())
            .createInitial(
                new GamePlayer("Alice", PieceColor.WHITE),
                new GamePlayer("Bob", PieceColor.BLACK),
                Optional.of(TimeControl.fifteenPlusTen()));
    assertTrue(sourceGame.move(move("f2", "f3")).accepted());
    assertTrue(sourceGame.move(move("e7", "e5")).accepted());
    assertTrue(sourceGame.move(move("g2", "g4")).accepted());
    assertTrue(sourceGame.move(move("d8", "h4")).accepted());

    var record = sourceGame.toRecord();
    AnalysisSessionSummary analysis = service.createFromGame(record);

    assertEquals(AnalysisOrigin.PLAYED_GAME, analysis.origin());
    assertEquals("Alice vs. Bob", analysis.title());
    assertEquals(GameResult.BLACK_WINS, analysis.sourceResult().orElseThrow());
    assertEquals(
        List.of("f3", "e5", "g4", "Qh4#"),
        service.notationLine(analysis.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());

    assertTrue(service.attemptMove(analysis.sessionId(), move("d7", "d5")).accepted());
    assertEquals(List.of("f3", "d5"),
        service.moveHistory(analysis.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());
    assertEquals(4, sourceGame.moveHistory().size());
    assertEquals("Qh4#", record.moves().getLast().ply().move().san().getValue());
  }

  private MoveCommand move(String from, String to) {
    return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
  }

  private void assertMove(
      AnalysisSessionId sessionId, String from, String to, String expectedSan) {
    var result = service.attemptMove(sessionId, move(from, to));
    assertTrue(result.accepted());
    assertEquals(expectedSan, result.move().orElseThrow().san().getValue());
  }

  private void verifyGameState(
      AnalysisSessionId sessionId,
      PieceColor expectedTurn,
      boolean expectedCheck,
      boolean expectedMate,
      boolean expectedStalemate,
      boolean expectedPromotion) {
    var state = service.gameState(sessionId);
    var position = service.currentPosition(sessionId);
    assertEquals(expectedTurn, state.whoseTurn());
    assertEquals(expectedCheck, state.check());
    assertEquals(expectedMate, state.mate());
    assertEquals(expectedStalemate, state.stalemate());
    assertEquals(
        expectedPromotion,
        position.lastMove().flatMap(move -> move.promotion()).isPresent());
  }
}
