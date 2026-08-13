package com.escontrela.lastmove.domain.game;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.SessionId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutable aggregate for one in-memory analysis session.
 *
 * <p>The root represents the initial position. Each accepted move becomes a child of the current
 * cursor, so a move made after going backwards creates a variation instead of discarding history.
 */
public final class GameSession {

  private final SessionId id;
  private final String title;
  private final GameSessionOrigin origin;
  private final PositionSnapshot initialPosition;
  private final Map<UUID, Ply> pliesById = new LinkedHashMap<>();
  private PositionSnapshot currentPosition;
  private UUID currentPlyId;
  private GameResult result;

  public GameSession(
      SessionId id, String title, GameSessionOrigin origin, PositionSnapshot initialPosition) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.initialPosition = Objects.requireNonNull(initialPosition, "initialPosition must not be null");
    this.currentPosition = initialPosition;
  }

  public SessionId id() { return id; }

  /** Returns the user-facing title assigned when this session was created. */
  public String title() { return title; }

  public GameSessionOrigin origin() { return origin; }

  public PositionSnapshot initialPosition() { return initialPosition; }

  public PositionSnapshot currentPosition() { return currentPosition; }

  public Optional<Ply> currentPly() {
    return Optional.ofNullable(currentPlyId).map(pliesById::get);
  }

  public GameSessionState gameState() {
    return new GameSessionState(
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

  public List<Ply> rootVariations() {
    return pliesById.values().stream().filter(ply -> ply.parentId().isEmpty()).toList();
  }

  /** Returns the selected line from the initial position through the cursor. */
  public List<Ply> currentLine() {
    List<Ply> reverse = new ArrayList<>();
    for (Ply ply = currentPly().orElse(null); ply != null; ply = ply.parentId().map(pliesById::get).orElse(null)) {
      reverse.add(ply);
    }
    List<Ply> line = new ArrayList<>(reverse.size());
    for (int index = reverse.size() - 1; index >= 0; index--) {
      line.add(reverse.get(index));
    }
    return List.copyOf(line);
  }

  /** The currently selected, linear history, derived from the authoritative variation tree. */
  public List<Ply> moveHistory() {
    return currentLine();
  }

  /**
   * Returns the full notation line selected by the current cursor, including moves ahead of it.
   *
   * <p>At each branch this follows the first continuation. When the user creates or selects an
   * alternative, that branch becomes the prefix and continuation shown to the notation view.
   */
  public List<Ply> notationLine() {
    List<Ply> line = new ArrayList<>(currentLine());
    List<Ply> candidates = currentPly().map(Ply::variations).orElseGet(this::rootVariations);
    while (!candidates.isEmpty()) {
      Ply next = candidates.getFirst();
      line.add(next);
      candidates = next.variations();
    }
    return List.copyOf(line);
  }

  /** Applies a validated result, adding it to the history and moving the cursor. */
  public void apply(MoveExecutionResult result) {
    Objects.requireNonNull(result, "result must not be null");
    if (!result.accepted()) {
      if (!currentPosition.equals(result.newSnapshot())) {
        throw new IllegalArgumentException("A rejected move must preserve the current position");
      }
      return;
    }

    MoveDescriptor move = result.move().orElseThrow(() -> new IllegalArgumentException("An accepted move requires a descriptor"));
    if (result.check() != result.newSnapshot().check()
        || result.mate() != result.newSnapshot().mate()
        || result.stalemate() != result.newSnapshot().stalemate()) {
      throw new IllegalArgumentException("Move result flags must match its resulting position");
    }
    UUID parentId = currentPlyId;
    Ply ply = new Ply(
        UUID.randomUUID(),
        parentId,
        move,
        result.newSnapshot(),
        currentPosition.fullmoveNumber(),
        currentPosition.activeColor());
    if (parentId != null) {
      pliesById.get(parentId).addVariation(ply);
    }
    pliesById.put(ply.id(), ply);
    currentPlyId = ply.id();
    currentPosition = result.newSnapshot();
    this.result = terminalResult(result);
  }

  public boolean previous() {
    if (currentPlyId == null) {
      return false;
    }
    Ply current = pliesById.get(currentPlyId);
    currentPlyId = current.parentId().orElse(null);
    currentPosition = currentPly().map(Ply::resultingPosition).orElse(initialPosition);
    result = null;
    return true;
  }

  /** Advances through the first available continuation of the selected line. */
  public boolean next() {
    List<Ply> candidates =
        currentPly().map(Ply::variations).orElseGet(this::rootVariations);
    if (candidates.isEmpty()) {
      return false;
    }
    return select(candidates.getFirst());
  }

  /** Returns to the initial position without deleting any line or variation. */
  public void first() {
    currentPlyId = null;
    currentPosition = initialPosition;
    result = null;
  }

  public boolean select(Ply ply) {
    Objects.requireNonNull(ply, "ply must not be null");
    if (!pliesById.containsKey(ply.id())) {
      return false;
    }
    currentPlyId = ply.id();
    currentPosition = ply.resultingPosition();
    result = currentPosition.mate() || currentPosition.stalemate() ? terminalResult(currentPosition) : null;
    return true;
  }

  private GameResult terminalResult(MoveExecutionResult result) {
    return result.mate() ? (currentPosition.activeColor() == PieceColor.WHITE ? GameResult.BLACK_WINS : GameResult.WHITE_WINS)
        : result.stalemate() ? GameResult.DRAW : null;
  }

  private GameResult terminalResult(PositionSnapshot position) {
    return position.mate() ? (position.activeColor() == PieceColor.WHITE ? GameResult.BLACK_WINS : GameResult.WHITE_WINS)
        : position.stalemate() ? GameResult.DRAW : null;
  }
}
