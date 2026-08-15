package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.AnalysisNodeSummary;
import com.escontrela.lastmove.application.dto.AnalysisNotationNode;
import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.GameRecord;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.ImportedPly;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.PgnGame;
import com.escontrela.lastmove.domain.service.FenService;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Application use case for creating, retaining, navigating and extending analysis sessions.
 *
 * <p>The service coordinates repositories and domain aggregates. Every requested move is executed
 * through a short-lived {@link ChessGame}; the accepted engine-neutral result is then applied to
 * the selected {@link AnalysisSession}. It owns no active-session selection and no chess rules.
 */
@Service
public final class AnalysisSessionService {

  private final AnalysisSessionRepository sessionRepository;
  private final ChessGameFactory gameFactory;
  private final AnalysisSessionFactory analysisSessionFactory;
  private final FenService fenService;

  public AnalysisSessionService(
      AnalysisSessionRepository sessionRepository,
      ChessGameFactory gameFactory,
      AnalysisSessionFactory analysisSessionFactory,
      FenService fenService) {
    this.sessionRepository =
        Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.analysisSessionFactory =
        Objects.requireNonNull(analysisSessionFactory, "analysisSessionFactory must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  /** Creates an analysis session at the standard chess initial position. */
  public AnalysisSessionSummary createInitialSession() {
    return register(
        "New game",
        AnalysisOrigin.INITIAL_POSITION,
        gameFactory.createAnalysisGame().currentPosition());
  }

  /** Creates an analysis session at the supplied FEN position. */
  public AnalysisSessionSummary createFenSession(Fen fen) {
    return register(
        "FEN position",
        AnalysisOrigin.FEN,
        gameFactory
            .createAnalysisGame(Objects.requireNonNull(fen, "fen must not be null"))
            .currentPosition());
  }

  /** Creates an empty PGN analysis session when only parsed headers are available. */
  public AnalysisSessionSummary createPgnSession(PgnGame game) {
    return createPgnSession(
        new ImportedPgnGame(Objects.requireNonNull(game, "game must not be null"), List.of()));
  }

  /** Creates and populates an analysis session from an imported PGN move tree. */
  public AnalysisSessionSummary createPgnSession(ImportedPgnGame importedGame) {
    ImportedPgnGame required =
        Objects.requireNonNull(importedGame, "importedGame must not be null");
    PgnGame game = required.game();
    PositionSnapshot initialPosition =
        game.getStartingFen()
            .map(gameFactory::createAnalysisGame)
            .orElseGet(gameFactory::createAnalysisGame)
            .currentPosition();
    AnalysisSession session =
        new AnalysisSession(
            AnalysisSessionId.random(),
            game.displayTitle(),
            AnalysisOrigin.PGN,
            initialPosition,
            game.getResult() == GameResult.UNKNOWN
                ? Optional.empty()
                : Optional.of(game.getResult()));
    importVariations(session, required.rootVariations());
    session.selectPreferredLine();
    session.first();
    if (!required.rootVariations().isEmpty()) {
      session.next();
    }
    sessionRepository.save(session);
    return summary(session);
  }

  /** Copies a progressive game record into a retained, independently mutable analysis session. */
  public AnalysisSessionSummary createFromGame(GameRecord gameRecord) {
    AnalysisSession session =
        analysisSessionFactory.fromGame(
            Objects.requireNonNull(gameRecord, "gameRecord must not be null"));
    sessionRepository.save(session);
    return summary(session);
  }

  /** Lists all retained sessions for a screen-specific session picker. */
  public List<AnalysisSessionSummary> listSessions() {
    return sessionRepository.findAllInDisplayOrder().stream().map(this::summary).toList();
  }

  /** Moves one retained session one place toward the start of the visible session list. */
  public boolean moveSessionUp(AnalysisSessionId sessionId) {
    return moveSession(sessionId, -1);
  }

  /** Moves one retained session one place toward the end of the visible session list. */
  public boolean moveSessionDown(AnalysisSessionId sessionId) {
    return moveSession(sessionId, 1);
  }

  /** Returns one retained session as a UI-safe summary. */
  public AnalysisSessionSummary sessionSummary(AnalysisSessionId sessionId) {
    return summary(session(sessionId));
  }

  /** Renames one retained study without changing its identity or active cursor. */
  public AnalysisSessionSummary renameSession(AnalysisSessionId sessionId, String newTitle) {
    AnalysisSession session = session(sessionId);
    session.rename(newTitle);
    sessionRepository.save(session);
    return summary(session);
  }

  /** Deletes one retained study and returns the summary that identified it. */
  public AnalysisSessionSummary deleteSession(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    if (!sessionRepository.deleteById(session.id())) {
      throw unknownSession(session.id());
    }
    return summary(session);
  }

  /** Returns the position currently selected by the session cursor. */
  public PositionSnapshot currentPosition(AnalysisSessionId sessionId) {
    return session(sessionId).currentPosition();
  }

  /** Returns the selected session position encoded as complete FEN text for export workflows. */
  public String currentFen(AnalysisSessionId sessionId) {
    return fenService.fromSnapshot(session(sessionId).currentPosition()).getValue();
  }

  /** Returns the rules state derived from the session's current position. */
  public GameStateSnapshot gameState(AnalysisSessionId sessionId) {
    return session(sessionId).currentState();
  }

  /** Returns the selected move line from the root through the cursor. */
  public List<Ply> moveHistory(AnalysisSessionId sessionId) {
    return session(sessionId).currentLine();
  }

  /** Returns the selected line plus its preferred continuation ahead of the cursor. */
  public List<Ply> notationLine(AnalysisSessionId sessionId) {
    return session(sessionId).notationLine();
  }

  /** Returns the complete visible notation line with selectable analysis-node identities. */
  public List<AnalysisNodeSummary> notationNodes(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    return session.notationNodes().stream().map(node -> nodeSummary(session, node)).toList();
  }

  /** Returns the complete recursive move tree with active-route and current-node markers. */
  public AnalysisNotationTree notationTree(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    Set<AnalysisNodeId> activeNodeIds =
        new HashSet<>(session.notationNodes().stream().map(AnalysisNode::id).toList());
    Optional<AnalysisNodeId> currentNodeId = session.currentNode().map(AnalysisNode::id);
    return new AnalysisNotationTree(
        projectNotationNodes(session, session.rootVariations(), activeNodeIds, currentNodeId),
        currentNodeId);
  }

  /** Returns the selectable variations that begin at the initial position. */
  public List<AnalysisNodeSummary> rootVariations(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    return session.rootVariations().stream()
        .map(node -> nodeSummary(session, node))
        .toList();
  }

  /** Returns the selectable continuations of one analysis node. */
  public List<AnalysisNodeSummary> continuations(
      AnalysisSessionId sessionId, AnalysisNodeId nodeId) {
    AnalysisSession session = session(sessionId);
    return session.continuations(nodeId).stream()
        .map(node -> nodeSummary(session, node))
        .toList();
  }

  /** Executes a move through {@link ChessGame} and applies its result to the analysis tree. */
  public MoveExecutionResult attemptMove(AnalysisSessionId sessionId, MoveCommand command) {
    AnalysisSession session = session(sessionId);
    ChessGame game = gameFactory.createAnalysisGame(session.currentPosition());
    MoveExecutionResult result = game.move(Objects.requireNonNull(command, "command must not be null"));
    session.apply(result);
    sessionRepository.save(session);
    return result;
  }

  /** Moves the cursor to the preceding ply and returns the displayed position. */
  public PositionSnapshot previous(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    session.previous();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Moves the cursor through its preferred continuation. */
  public PositionSnapshot next(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    session.next();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Rewinds to the initial position without deleting moves or variations. */
  public PositionSnapshot first(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    session.first();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Advances to the final position of the cursor's preferred continuation. */
  public PositionSnapshot last(AnalysisSessionId sessionId) {
    AnalysisSession session = session(sessionId);
    session.last();
    sessionRepository.save(session);
    return session.currentPosition();
  }

  /** Selects a structural analysis node as the session cursor. */
  public PositionSnapshot select(AnalysisSessionId sessionId, AnalysisNodeId nodeId) {
    AnalysisSession session = session(sessionId);
    if (!session.select(nodeId)) {
      throw new IllegalArgumentException("The node does not belong to session " + sessionId.value());
    }
    sessionRepository.save(session);
    return session.currentPosition();
  }

  private AnalysisSessionSummary register(
      String title, AnalysisOrigin origin, PositionSnapshot initialPosition) {
    AnalysisSession session =
        new AnalysisSession(AnalysisSessionId.random(), title, origin, initialPosition);
    sessionRepository.save(session);
    return summary(session);
  }

  private boolean moveSession(AnalysisSessionId sessionId, int offset) {
    AnalysisSessionId required = Objects.requireNonNull(sessionId, "sessionId must not be null");
    List<AnalysisSession> ordered = sessionRepository.findAllInDisplayOrder();
    int currentIndex =
        java.util.stream.IntStream.range(0, ordered.size())
            .filter(index -> ordered.get(index).id().equals(required))
            .findFirst()
            .orElseThrow(() -> unknownSession(required));
    int targetIndex = currentIndex + offset;
    if (targetIndex < 0 || targetIndex >= ordered.size()) {
      return false;
    }
    return sessionRepository.moveToIndex(required, targetIndex);
  }

  private void importVariations(AnalysisSession session, List<ImportedPly> variations) {
    for (ImportedPly variation : variations) {
      session.apply(variation.execution());
      importVariations(session, variation.variations());
      session.previous();
    }
  }

  private AnalysisSession session(AnalysisSessionId sessionId) {
    AnalysisSessionId required = Objects.requireNonNull(sessionId, "sessionId must not be null");
    return sessionRepository.findById(required).orElseThrow(() -> unknownSession(required));
  }

  private NoSuchElementException unknownSession(AnalysisSessionId sessionId) {
    return new NoSuchElementException("No open analysis session with id " + sessionId.value());
  }

  private AnalysisSessionSummary summary(AnalysisSession session) {
    return new AnalysisSessionSummary(
        session.id(),
        session.title(),
        session.origin(),
        session.currentPosition(),
        session.sourceResult());
  }

  private AnalysisNodeSummary nodeSummary(AnalysisSession session, AnalysisNode node) {
    return new AnalysisNodeSummary(
        node.id(), node.ply(), session.continuations(node.id()).size());
  }

  private List<AnalysisNotationNode> projectNotationNodes(
      AnalysisSession session,
      List<AnalysisNode> nodes,
      Set<AnalysisNodeId> activeNodeIds,
      Optional<AnalysisNodeId> currentNodeId) {
    java.util.ArrayList<AnalysisNotationNode> projected = new java.util.ArrayList<>(nodes.size());
    for (int index = 0; index < nodes.size(); index++) {
      AnalysisNode node = nodes.get(index);
      projected.add(
          new AnalysisNotationNode(
              node.id(),
              node.ply(),
              projectNotationNodes(
                  session, session.continuations(node.id()), activeNodeIds, currentNodeId),
              index == 0,
              activeNodeIds.contains(node.id()),
              currentNodeId.filter(node.id()::equals).isPresent()));
    }
    return List.copyOf(projected);
  }
}
