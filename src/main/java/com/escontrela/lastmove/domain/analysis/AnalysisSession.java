package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a navigable chess study with non-destructive move variations.
 *
 * <p>The session is a thin shell over an {@link AnalysisDocument}: it owns the session identity,
 * title and origin, while the document owns the chess content, the reading state and every
 * navigation behavior. The {@code AnalysisSession} public API is intentionally stable so sessions
 * and persisted study chapters behave identically on the board.
 */
public final class AnalysisSession {

  private final AnalysisSessionId id;
  private String title;
  private final AnalysisOrigin origin;
  private final AnalysisDocument document;

  public AnalysisSession(
      AnalysisSessionId id,
      String title,
      AnalysisOrigin origin,
      PositionSnapshot initialPosition) {
    this(id, title, origin, initialPosition, Optional.empty());
  }

  /** Creates a session that retains the declared result of its imported source game. */
  public AnalysisSession(
      AnalysisSessionId id,
      String title,
      AnalysisOrigin origin,
      PositionSnapshot initialPosition,
      Optional<GameResult> sourceResult) {
    this(id, title, origin, new AnalysisDocument(initialPosition, sourceResult));
  }

  /** Wraps an existing analysis document as an independently navigable session. */
  public AnalysisSession(
      AnalysisSessionId id, String title, AnalysisOrigin origin, AnalysisDocument document) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.title = requireTitle(title);
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.document = Objects.requireNonNull(document, "document must not be null");
  }

  public AnalysisSessionId id() {
    return id;
  }

  public String title() {
    return title;
  }

  /** Renames this study while preserving its identity, tree and navigation cursor. */
  public void rename(String newTitle) {
    title = requireTitle(newTitle);
  }

  public AnalysisOrigin origin() {
    return origin;
  }

  public AnalysisDocument document() {
    return document;
  }

  public PositionSnapshot initialPosition() {
    return document.initialPosition();
  }

  public PositionSnapshot currentPosition() {
    return document.currentPosition();
  }

  /** Returns the declared result of the imported game, independent of cursor position. */
  public Optional<GameResult> sourceResult() {
    return document.sourceResult();
  }

  public Optional<AnalysisNode> currentNode() {
    return document.currentNode();
  }

  public Optional<Ply> currentPly() {
    return document.currentPly();
  }

  public Optional<GameResult> result() {
    return document.result();
  }

  public GameStateSnapshot currentState() {
    return document.currentState();
  }

  public List<AnalysisNode> rootVariations() {
    return document.rootVariations();
  }

  public List<AnalysisNode> continuations(AnalysisNodeId nodeId) {
    return document.continuations(nodeId);
  }

  /** Returns the selected line from the initial position through the cursor. */
  public List<Ply> currentLine() {
    return document.currentLine();
  }

  /** Returns the selected line plus its preferred continuation ahead of the cursor. */
  public List<Ply> notationLine() {
    return document.notationLine();
  }

  /**
   * Returns the selectable structural nodes shown in notation order.
   *
   * <p>The result contains the path through the cursor followed by the preferred continuation, so
   * a UI can render the complete visible line and select any displayed ply by node identity.
   */
  public List<AnalysisNode> notationNodes() {
    return document.notationNodes();
  }

  /**
   * Adds an accepted move at the cursor, or selects an identical existing continuation.
   *
   * <p>Rejected results preserve both the cursor and the tree.
   */
  public void apply(MoveExecutionResult execution) {
    document.apply(execution);
  }

  /** Moves the cursor to its parent, or to the initial position from a root move. */
  public boolean previous() {
    return document.previous();
  }

  /** Advances through the first preferred continuation from the cursor. */
  public boolean next() {
    return document.next();
  }

  /** Returns to the initial position without deleting any move or variation. */
  public void first() {
    document.first();
  }

  /** Advances to the final node of the preferred continuation from the current cursor. */
  public void last() {
    document.last();
  }

  /** Selects a node belonging to this session. */
  public boolean select(AnalysisNodeId nodeId) {
    return document.select(nodeId);
  }

  /**
   * Restores the tree's first-child line as the active route without moving the current cursor.
   *
   * <p>PGN import uses this after constructing every sibling variation so the recorded main line,
   * which is inserted first, remains the initial navigation route.
   */
  public void selectPreferredLine() {
    document.selectPreferredLine();
  }

  private String requireTitle(String value) {
    String required = Objects.requireNonNull(value, "title must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return required;
  }
}
