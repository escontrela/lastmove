package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.GameSessionSummary;
import com.escontrela.lastmove.application.session.GameSessionRepository;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application use case for the lifecycle of in-memory chess-analysis sessions.
 *
 * <p>It creates sessions, delegates move validation to {@link GameMoveService}, applies accepted
 * outcomes to the domain aggregate, and exposes session navigation. UI workflows decide which
 * stored session is currently selected.
 */
@Service
public final class GameSessionService {

  private final GameSessionRepository sessionRepository;
  private final GameMoveService moveService;

  public GameSessionService(
      GameSessionRepository sessionRepository, GameMoveService moveService) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.moveService = Objects.requireNonNull(moveService, "moveService must not be null");
  }

  /** Creates a session at the standard chess initial position. */
  public GameSessionSummary createInitialSession() {
    return register(
        "New game",
        GameSessionOrigin.INITIAL_POSITION,
        moveService.startingPosition());
  }

  /** Creates a session at the supplied FEN position. */
  public GameSessionSummary createFenSession(Fen fen) {
    Objects.requireNonNull(fen, "fen must not be null");
    return register("FEN position", GameSessionOrigin.FEN, moveService.snapshotFor(fen));
  }

  /**
   * Creates a session for a parsed PGN game.
   *
   * <p>The session starts at the PGN's declared FEN, or the normal initial position when the PGN
   * has none. Importing the PGN move tree itself is deliberately the next vertical slice.
   */
  public GameSessionSummary createPgnSession(PgnGame game) {
    Objects.requireNonNull(game, "game must not be null");
    return createPgnSession(new ImportedPgnGame(game, List.of()));
  }

  /** Creates and populates a session from an imported PGN move tree. */
  public GameSessionSummary createPgnSession(ImportedPgnGame importedGame) {
    Objects.requireNonNull(importedGame, "importedGame must not be null");
    PgnGame game = importedGame.game();
    PositionSnapshot initialPosition =
        game.getStartingFen().map(moveService::snapshotFor).orElseGet(moveService::startingPosition);
    GameSession session = registerSession(game.displayTitle(), GameSessionOrigin.PGN, initialPosition);
    importVariations(session, importedGame.rootVariations());
    session.first();
    if (!importedGame.rootVariations().isEmpty()) {
      session.next();
    }
    sessionRepository.save(session);
    return summary(session);
  }

  /** Lists every stored session for a screen-specific session picker. */
  public List<GameSessionSummary> listSessions() {
    return sessionRepository.findAllByMostRecent().stream()
        .map(
            session ->
                new GameSessionSummary(
                    session.id(),
                    session.title(),
                    session.origin(),
                    session.currentPosition()))
        .toList();
  }

  /** Returns one stored session as a UI-safe summary. */
  public GameSessionSummary sessionSummary(SessionId sessionId) {
    return summary(session(sessionId));
  }

  /** Returns the current renderable position for one open session. */
  public PositionSnapshot currentPosition(SessionId sessionId) {
    return session(sessionId).currentPosition();
  }

  /** Returns the selected move line for rendering in the notation panel. */
  public List<Ply> moveHistory(SessionId sessionId) {
    return session(sessionId).moveHistory();
  }

  /** Returns the complete notation line around the current cursor for the move-list view. */
  public List<Ply> notationLine(SessionId sessionId) {
    return session(sessionId).notationLine();
  }

  /** Validates and applies a move to the specified open session. */
  public MoveExecutionResult attemptMove(SessionId sessionId, MoveCommand command) {
    GameSession session = session(sessionId);
    MoveExecutionResult result = moveService.validate(session.currentPosition(), command);
    session.apply(result);
    sessionRepository.save(session);
    return result;
  }

  /** Moves the cursor to the preceding ply and returns the newly displayed position. */
  public PositionSnapshot previous(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.previous();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Moves the cursor through its first continuation and returns the newly displayed position. */
  public PositionSnapshot next(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.next();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Rewinds a session to its initial position without removing its recorded history. */
  public PositionSnapshot first(SessionId sessionId) {
    GameSession session = session(sessionId);
    session.first();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Selects an existing variation node as the current session cursor. */
  public PositionSnapshot select(SessionId sessionId, Ply ply) {
    GameSession session = session(sessionId);
    if (!session.select(ply)) {
      throw new IllegalArgumentException("The ply does not belong to session " + sessionId.value());
    }
    sessionRepository.save(session);
    return session.currentPosition();
  }

  private GameSessionSummary register(
      String title, GameSessionOrigin origin, PositionSnapshot initialPosition) {
    GameSession session = registerSession(title, origin, initialPosition);
    sessionRepository.save(session);
    return summary(session);
  }

  private GameSession registerSession(
      String title, GameSessionOrigin origin, PositionSnapshot initialPosition) {
    return new GameSession(SessionId.random(), title, origin, initialPosition);
  }

  private void importVariations(GameSession session, List<ImportedPly> variations) {
    for (ImportedPly variation : variations) {
      session.apply(variation.execution());
      importVariations(session, variation.variations());
      session.previous();
    }
  }

  private GameSession session(SessionId sessionId) {
    return sessionRepository.findById(sessionId).orElseThrow(() -> unknownSession(sessionId));
  }

  private NoSuchElementException unknownSession(SessionId sessionId) {
    return new NoSuchElementException("No open session with id " + sessionId.value());
  }

  private GameSessionSummary summary(GameSession session) {
    return new GameSessionSummary(
        session.id(), session.title(), session.origin(), session.currentPosition());
  }
}
