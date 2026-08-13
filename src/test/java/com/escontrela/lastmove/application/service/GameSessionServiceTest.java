package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.dto.GameSessionSummary;
import com.escontrela.lastmove.infrastructure.session.InMemoryGameSessionRepository;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameSessionOrigin;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoMoveValidator;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameSessionServiceTest {

  private final GameSessionService service =
      new GameSessionService(
          new InMemoryGameSessionRepository(), new GameMoveService(new ChesspressoMoveValidator()));

  @Test
  void createsAndListsIndependentInitialAndFenSessions() {
    GameSessionSummary initial = service.createInitialSession();
    GameSessionSummary fen = service.createFenSession(Fen.of("8/8/8/8/8/8/8/K6k b - - 7 42"));

    assertEquals(GameSessionOrigin.INITIAL_POSITION, initial.origin());
    assertEquals(GameSessionOrigin.FEN, fen.origin());
    assertEquals(2, service.listSessions().size());
    assertEquals(fen.sessionId(), service.listSessions().getFirst().sessionId());
    assertEquals(PieceColor.WHITE, service.currentPosition(initial.sessionId()).activeColor());
    assertEquals(PieceColor.BLACK, service.currentPosition(fen.sessionId()).activeColor());
  }

  @Test
  void createsPgnSessionAtItsDeclaredStartingFen() {
    PgnGame game =
        new PgnGame(
            Map.of("White", "Ada", "Black", "Grace", "Event", "Test"),
            "",
            GameResult.UNKNOWN,
            Fen.of("8/8/8/8/8/8/8/K6k b - - 7 42"));

    GameSessionSummary session = service.createPgnSession(game);

    assertEquals(GameSessionOrigin.PGN, session.origin());
    assertEquals("Ada vs. Grace – Test", session.title());
    assertEquals(PieceColor.BLACK, session.currentPosition().activeColor());
    assertEquals(42, session.currentPosition().fullmoveNumber());
  }

  @Test
  void appliesMovesAndNavigatesOnlyWithinTheSelectedSession() {
    GameSessionSummary session = service.createInitialSession();

    assertTrue(
        service
            .attemptMove(
                session.sessionId(),
                new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty()))
            .accepted());
    assertEquals(PieceColor.BLACK, service.currentPosition(session.sessionId()).activeColor());

    service.previous(session.sessionId());
    assertEquals(PieceColor.WHITE, service.currentPosition(session.sessionId()).activeColor());

    service.next(session.sessionId());
    assertEquals(PieceColor.BLACK, service.currentPosition(session.sessionId()).activeColor());
  }
}
