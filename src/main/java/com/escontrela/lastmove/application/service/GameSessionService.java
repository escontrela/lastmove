package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.GameSessionSummary;
import com.escontrela.lastmove.application.session.InMemoryGameSessionCatalog;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.GameSession;
import com.escontrela.lastmove.domain.game.GameSessionOrigin;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.ImportedPly;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.domain.service.PgnService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application use case for the lifecycle of in-memory chess-analysis sessions.
 *
 * <p>It creates and activates sessions, delegates move validation to {@link GameMoveService},
 * applies accepted outcomes to the domain aggregate, and exposes session navigation. The catalog
 * is intentionally in memory only; persistence is outside this use case.
 */
@Service
public final class GameSessionService {

  private final InMemoryGameSessionCatalog catalog;
  private final GameMoveService moveService;
  private final PgnService pgnService;

  public GameSessionService(
      InMemoryGameSessionCatalog catalog, GameMoveService moveService, PgnService pgnService) {
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.moveService = Objects.requireNonNull(moveService, "moveService must not be null");
    this.pgnService = Objects.requireNonNull(pgnService, "pgnService must not be null");
  }

  /** Creates and activates a session at the standard chess initial position. */
  public GameSessionSummary createInitialSession() {
    return register(
        "New game",
        GameSessionOrigin.INITIAL_POSITION,
        moveService.startingPosition());
  }

  /** Creates and activates a session at the supplied FEN position. */
  public GameSessionSummary createFenSession(Fen fen) {
    Objects.requireNonNull(fen, "fen must not be null");
    return register("FEN position", GameSessionOrigin.FEN, moveService.snapshotFor(fen));
  }

  /**
   * Creates and activates a session for a parsed PGN game.
   *
   * <p>The session starts at the PGN's declared FEN, or the normal initial position when the PGN
   * has none. Importing the PGN move tree itself is deliberately the next vertical slice.
   */
  public GameSessionSummary createPgnSession(PgnGame game) {
    Objects.requireNonNull(game, "game must not be null");
    return createPgnSession(new ImportedPgnGame(game, List.of()));
  }

  /** Creates, activates and populates a session from an imported PGN move tree. */
  public GameSessionSummary createPgnSession(ImportedPgnGame importedGame) {
    Objects.requireNonNull(importedGame, "importedGame must not be null");
    PgnGame game = importedGame.game();
    PositionSnapshot initialPosition =
        game.getStartingFen().map(moveService::snapshotFor).orElseGet(moveService::startingPosition);
    GameSession session = registerSession(pgnService.title(game), GameSessionOrigin.PGN, initialPosition);
    importVariations(session, importedGame.rootVariations());
    session.first();
    if (!importedGame.rootVariations().isEmpty()) {
      session.next();
    }
    return summary(session, true);
  }

  /** Lists every currently open session for the in-memory session picker. */
  public List<GameSessionSummary> listSessions() {
    return catalog.all().stream()
        .map(
            session ->
                new GameSessionSummary(
                    session.id(),
                    titleFor(session),
                    session.origin(),
                    catalog.active().map(GameSession::id).filter(session.id()::equals).isPresent(),
                    session.currentPosition()))
        .toList();
  }

  /** Activates an existing session. */
  public GameSessionSummary activate(SessionId sessionId) {
    if (!catalog.activate(sessionId)) {
      throw unknownSession(sessionId);
    }
    return summary(session(sessionId), true);
  }

  /** Returns the current active session summary. */
  public GameSessionSummary activeSession() {
    return catalog.active().map(session -> summary(session, true)).orElseThrow(() -> new NoSuchElementException("No active session"));
  }

  /** Returns the current renderable position for one open session. */
  public PositionSnapshot currentPosition(SessionId sessionId) {
    return session(sessionId).currentPosition();
  }

  /** Returns the selected move line for rendering in the notation panel. */
  public List<Ply> moveHistory(SessionId sessionId) {
    return session(sessionId).moveHistory();
  }

  /** Validates and applies a move to the specified open session. */
  public MoveExecutionResult attemptMove(SessionId sessionId, MoveCommand command) {
    GameSession session = session(sessionId);
    MoveExecutionResult result = moveService.validate(session.currentPosition(), command);
    session.apply(result);
    return result;
  }

  /** Moves the cursor to the preceding ply and returns the newly displayed position. */
  public PositionSnapshot previous(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.previous();
    return session.currentPosition();
  }

  /** Moves the cursor through its first continuation and returns the newly displayed position. */
  public PositionSnapshot next(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.next();
    return session.currentPosition();
  }

  /** Rewinds a session to its initial position without removing its recorded history. */
  public PositionSnapshot first(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.first();
    return session.currentPosition();
  }

  /** Selects an existing variation node as the current session cursor. */
  public PositionSnapshot select(SessionId sessionId, Ply ply) {
    GameSession session = session(sessionId);
    if (!session.select(ply)) {
      throw new IllegalArgumentException("The ply does not belong to session " + sessionId.value());
    }
    return session.currentPosition();
  }

  private GameSessionSummary register(
      String title, GameSessionOrigin origin, PositionSnapshot initialPosition) {
    return summary(registerSession(title, origin, initialPosition), true);
  }

  private GameSession registerSession(
      String title, GameSessionOrigin origin, PositionSnapshot initialPosition) {
    GameSession session = new GameSession(SessionId.random(), origin, initialPosition);
    catalog.addAndActivate(session, title);
    return session;
  }

  private void importVariations(GameSession session, List<ImportedPly> variations) {
    for (ImportedPly variation : variations) {
      session.apply(variation.execution());
      importVariations(session, variation.variations());
      session.previous();
    }
  }

  private GameSession session(SessionId sessionId) {
    return catalog.find(sessionId).orElseThrow(() -> unknownSession(sessionId));
  }

  private NoSuchElementException unknownSession(SessionId sessionId) {
    return new NoSuchElementException("No open session with id " + sessionId.value());
  }

  private GameSessionSummary summary(GameSession session, boolean active) {
    return summary(session, active, titleFor(session));
  }

  private GameSessionSummary summary(GameSession session, boolean active, String title) {
    return new GameSessionSummary(
        session.id(), title, session.origin(), active, session.currentPosition());
  }

  private String titleFor(GameSession session) {
    return catalog.titleOf(session.id()).orElseThrow();
  }
}
