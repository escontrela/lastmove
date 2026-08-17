package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Composed analysis state shared by ephemeral {@link AnalysisSession} and persisted {@code
 * StudyChapter}.
 *
 * <p>Owns the {@link AnalysisContent} (chess content), the {@link ChapterNavigation} (reading
 * state) and every behavior that coordinates them: applying moves, moving the cursor and projecting
 * the visible notation line. A deep copy produces an independent document used when archiving a
 * session into a study chapter.
 */
public final class AnalysisDocument {

  private final AnalysisContent content;
  private ChapterNavigation navigation;
  private PositionSnapshot currentPosition;
  private GameResult result;

  public AnalysisDocument(PositionSnapshot initialPosition, Optional<GameResult> sourceResult) {
    this(new AnalysisContent(initialPosition, sourceResult, new AnalysisTree()), new ChapterNavigation());
  }

  /** Rehydrates a document from persisted content and reading state. */
  public AnalysisDocument(AnalysisContent content, ChapterNavigation navigation) {
    this.content = Objects.requireNonNull(content, "content must not be null");
    this.navigation = Objects.requireNonNull(navigation, "navigation must not be null");
    this.currentPosition =
        navigation
            .currentNodeId()
            .flatMap(content.tree()::find)
            .map(node -> node.ply().resultingPosition())
            .orElse(content.initialPosition());
    this.result = terminalResult(currentPosition).orElse(null);
  }

  public AnalysisContent content() {
    return content;
  }

  public ChapterNavigation navigation() {
    return navigation;
  }

  public PositionSnapshot initialPosition() {
    return content.initialPosition();
  }

  public Optional<GameResult> sourceResult() {
    return content.sourceResult();
  }

  public PositionSnapshot currentPosition() {
    return currentPosition;
  }

  public Optional<AnalysisNode> currentNode() {
    return navigation.currentNodeId().flatMap(content.tree()::find);
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
    return content.tree().roots();
  }

  public List<AnalysisNode> continuations(AnalysisNodeId nodeId) {
    return content.tree().children(nodeId);
  }

  /** Returns the selected line from the initial position through the cursor. */
  public List<Ply> currentLine() {
    return currentNodeLine().stream().map(AnalysisNode::ply).toList();
  }

  /** Returns the selected line plus its preferred continuation ahead of the cursor. */
  public List<Ply> notationLine() {
    return notationNodes().stream().map(AnalysisNode::ply).toList();
  }

  /**
   * Returns the selectable structural nodes shown in notation order.
   *
   * <p>The result contains the path through the cursor followed by the preferred continuation, so
   * a UI can render the complete visible line and select any displayed ply by node identity.
   */
  public List<AnalysisNode> notationNodes() {
    List<AnalysisNode> line = new ArrayList<>(currentNodeLine());
    AnalysisNodeId parentId = navigation.currentNodeId().orElse(null);
    Optional<AnalysisNode> continuation = selectedContinuationAt(parentId);
    while (continuation.isPresent()) {
      AnalysisNode next = continuation.orElseThrow();
      line.add(next);
      parentId = next.id();
      continuation = selectedContinuationAt(parentId);
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
        navigation.currentNodeId().map(parent -> content.tree().addChild(parent, ply))
            .orElseGet(() -> content.tree().addRoot(ply));
    select(node.id());
  }

  /** Moves the cursor to its parent, or to the initial position from a root move. */
  public boolean previous() {
    if (navigation.currentNodeId().isEmpty()) {
      return false;
    }
    AnalysisNode current = content.tree().node(navigation.currentNodeId().orElseThrow());
    navigation =
        navigation.withCurrentNodeId(current.parentId()).withLastVisitedAt(Instant.now());
    refreshCurrentState();
    return true;
  }

  /** Advances through the first preferred continuation from the cursor. */
  public boolean next() {
    return selectedContinuationAt(navigation.currentNodeId().orElse(null))
        .map(node -> select(node.id()))
        .orElse(false);
  }

  /** Returns to the initial position without deleting any move or variation. */
  public void first() {
    navigation = navigation.withCurrentNodeId(Optional.empty()).withLastVisitedAt(Instant.now());
    refreshCurrentState();
  }

  /** Advances to the final node of the preferred continuation from the current cursor. */
  public void last() {
    while (next()) {
      // next() selects the preferred continuation until the branch has no more nodes.
    }
  }

  /** Selects a node belonging to this document. */
  public boolean select(AnalysisNodeId nodeId) {
    Optional<AnalysisNode> node =
        content.tree().find(Objects.requireNonNull(nodeId, "nodeId must not be null"));
    if (node.isEmpty()) {
      return false;
    }
    rememberSelectedLine(nodeId);
    refreshCurrentState();
    return true;
  }

  /**
   * Restores the tree's first-child line as the active route without moving the current cursor.
   *
   * <p>PGN import uses this after constructing every sibling variation so the recorded main line,
   * which is inserted first, remains the initial navigation route.
   */
  public void selectPreferredLine() {
    List<AnalysisNode> roots = content.tree().roots();
    Optional<AnalysisNodeId> selectedRoot = roots.isEmpty() ? Optional.empty() : Optional.of(roots.getFirst().id());
    Map<AnalysisNodeId, AnalysisNodeId> continuations = new LinkedHashMap<>();
    AnalysisNode current = roots.isEmpty() ? null : roots.getFirst();
    while (current != null) {
      List<AnalysisNode> children = content.tree().children(current.id());
      if (children.isEmpty()) {
        current = null;
      } else {
        AnalysisNode child = children.getFirst();
        continuations.put(current.id(), child.id());
        current = child;
      }
    }
    navigation =
        navigation.withSelectedLine(selectedRoot, Map.copyOf(continuations)).withLastVisitedAt(Instant.now());
  }

  private List<AnalysisNode> candidatesAtCursor() {
    return navigation.currentNodeId().map(content.tree()::children)
        .orElseGet(() -> content.tree().roots());
  }

  private Optional<AnalysisNode> selectedContinuationAt(AnalysisNodeId parentId) {
    List<AnalysisNode> candidates = parentId == null ? content.tree().roots() : content.tree().children(parentId);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    AnalysisNodeId selectedId =
        parentId == null
            ? navigation.selectedRootNodeId().orElse(null)
            : navigation.selectedContinuationIds().get(parentId);
    return candidates.stream()
        .filter(candidate -> candidate.id().equals(selectedId))
        .findFirst()
        .or(() -> Optional.of(candidates.getFirst()));
  }

  private void rememberSelectedLine(AnalysisNodeId nodeId) {
    AnalysisNode previous = null;
    Map<AnalysisNodeId, AnalysisNodeId> continuations = new LinkedHashMap<>();
    Optional<AnalysisNodeId> selectedRoot = Optional.empty();
    for (AnalysisNode node : content.tree().lineTo(nodeId)) {
      if (previous == null) {
        selectedRoot = Optional.of(node.id());
      } else {
        continuations.put(previous.id(), node.id());
      }
      previous = node;
    }
    navigation =
        navigation.withCurrentNodeId(Optional.of(nodeId))
            .withSelectedLine(selectedRoot, Map.copyOf(continuations))
            .withLastVisitedAt(Instant.now());
  }

  private List<AnalysisNode> currentNodeLine() {
    return navigation.currentNodeId().map(content.tree()::lineTo).orElse(List.of());
  }

  private void refreshCurrentState() {
    currentPosition =
        currentNode().map(node -> node.ply().resultingPosition()).orElse(content.initialPosition());
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
