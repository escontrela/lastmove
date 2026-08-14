package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for a navigable chess study with non-destructive move variations.
 *
 * <p>The session owns the current cursor and position, while {@link AnalysisTree} exclusively owns
 * the topology. Applying a new move at a node with continuations adds an alternative child; applying
 * an already existing continuation selects it instead of duplicating or deleting the line.
 */
public final class AnalysisSession {

  private final AnalysisSessionId id;
  private final String title;
  private final AnalysisOrigin origin;
  private final PositionSnapshot initialPosition;
  private final Optional<GameResult> sourceResult;
  private final AnalysisTree tree;
  private PositionSnapshot currentPosition;
  private AnalysisNodeId currentNodeId;
  private GameResult result;

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
    this(id, title, origin, initialPosition, sourceResult, new AnalysisTree());
  }

  AnalysisSession(
      AnalysisSessionId id,
      String title,
      AnalysisOrigin origin,
      PositionSnapshot initialPosition,
      Optional<GameResult> sourceResult,
      AnalysisTree tree) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.initialPosition =
        Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    this.sourceResult = Objects.requireNonNull(sourceResult, "sourceResult must not be null");
    this.currentPosition = initialPosition;
    this.tree = Objects.requireNonNull(tree, "tree must not be null");
    this.result = terminalResult(initialPosition).orElse(null);
  }

  public AnalysisSessionId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public AnalysisOrigin origin() {
    return origin;
  }

  public PositionSnapshot initialPosition() {
    return initialPosition;
  }

  public PositionSnapshot currentPosition() {
    return currentPosition;
  }

  /** Returns the declared result of the imported game, independent of cursor position. */
  public Optional<GameResult> sourceResult() {
    return sourceResult;
  }

  public Optional<AnalysisNode> currentNode() {
    return Optional.ofNullable(currentNodeId).flatMap(tree::find);
  }

  public Optional<Ply> currentPly() {
    return currentNode().map(AnalysisNode::ply);
  }

  public Optional<GameResult> result() {
    return Optional.ofNullable(result);
  }

  public GameStateSnapshot currentState() {
    return new GameStateSnapshot(
        currentPosition.activeColor(),
        currentPosition.castlingRights(),
        currentPosition.enPassantTarget(),
        currentPosition.halfmoveClock(),
        currentPosition.fullmoveNumber(),
        currentPosition.check(),
        currentPosition.mate(),
        currentPosition.stalemate(),
        Optional.ofNullable(result));
  }

  public List<AnalysisNode> rootVariations() {
    return tree.roots();
  }

  public List<AnalysisNode> continuations(AnalysisNodeId nodeId) {
    return tree.children(nodeId);
  }

  /** Returns the selected line from the initial position through the cursor. */
  public List<Ply> currentLine() {
    if (currentNodeId == null) {
      return List.of();
    }
    return tree.lineTo(currentNodeId).stream().map(AnalysisNode::ply).toList();
  }

  /** Returns the selected line plus its preferred continuation ahead of the cursor. */
  public List<Ply> notationLine() {
    List<Ply> line = new ArrayList<>(currentLine());
    List<AnalysisNode> candidates =
        currentNodeId == null ? tree.roots() : tree.children(currentNodeId);
    while (!candidates.isEmpty()) {
      AnalysisNode next = candidates.getFirst();
      line.add(next.ply());
      candidates = tree.children(next.id());
    }
    return List.copyOf(line);
  }

  /**
   * Adds an accepted move at the cursor, or selects an identical existing continuation.
   *
   * <p>Rejected results preserve both the cursor and the tree.
   */
  public void apply(MoveExecutionResult execution) {
    Objects.requireNonNull(execution, "execution must not be null");
    if (!execution.accepted()) {
      if (!currentPosition.equals(execution.newSnapshot())) {
        throw new IllegalArgumentException("A rejected move must preserve the current position");
      }
      return;
    }

    MoveDescriptor move =
        execution
            .move()
            .orElseThrow(() -> new IllegalArgumentException("An accepted move requires a descriptor"));
    validateAcceptedResult(execution);
    Optional<AnalysisNode> existing =
        candidatesAtCursor().stream().filter(node -> node.ply().move().equals(move)).findFirst();
    if (existing.isPresent()) {
      if (!existing.get().ply().resultingPosition().equals(execution.newSnapshot())) {
        throw new IllegalStateException("An existing continuation must produce the same position");
      }
      select(existing.get().id());
      return;
    }

    Ply ply =
        new Ply(
            UUID.randomUUID(),
            move,
            execution.newSnapshot(),
            currentPosition.fullmoveNumber(),
            currentPosition.activeColor());
    AnalysisNode node =
        currentNodeId == null ? tree.addRoot(ply) : tree.addChild(currentNodeId, ply);
    select(node.id());
  }

  /** Moves the cursor to its parent, or to the initial position from a root move. */
  public boolean previous() {
    if (currentNodeId == null) {
      return false;
    }
    AnalysisNode current = tree.node(currentNodeId);
    currentNodeId = current.parentId().orElse(null);
    refreshCurrentState();
    return true;
  }

  /** Advances through the first preferred continuation from the cursor. */
  public boolean next() {
    List<AnalysisNode> candidates = candidatesAtCursor();
    return !candidates.isEmpty() && select(candidates.getFirst().id());
  }

  /** Returns to the initial position without deleting any move or variation. */
  public void first() {
    currentNodeId = null;
    refreshCurrentState();
  }

  /** Selects a node belonging to this session. */
  public boolean select(AnalysisNodeId nodeId) {
    Optional<AnalysisNode> node = tree.find(Objects.requireNonNull(nodeId, "nodeId must not be null"));
    if (node.isEmpty()) {
      return false;
    }
    currentNodeId = nodeId;
    refreshCurrentState();
    return true;
  }

  private List<AnalysisNode> candidatesAtCursor() {
    return currentNodeId == null ? tree.roots() : tree.children(currentNodeId);
  }

  private void refreshCurrentState() {
    currentPosition = currentNode().map(node -> node.ply().resultingPosition()).orElse(initialPosition);
    result = terminalResult(currentPosition).orElse(null);
  }

  private void validateAcceptedResult(MoveExecutionResult execution) {
    PositionSnapshot position = execution.newSnapshot();
    if (execution.check() != position.check()
        || execution.mate() != position.mate()
        || execution.stalemate() != position.stalemate()) {
      throw new IllegalArgumentException("Move result flags must match its resulting position");
    }
  }

  private Optional<GameResult> terminalResult(PositionSnapshot position) {
    if (position.mate()) {
      return Optional.of(
          position.activeColor() == PieceColor.WHITE
              ? GameResult.BLACK_WINS
              : GameResult.WHITE_WINS);
    }
    return position.stalemate() ? Optional.of(GameResult.DRAW) : Optional.empty();
  }
}
