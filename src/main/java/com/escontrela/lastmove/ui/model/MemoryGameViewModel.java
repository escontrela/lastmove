package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.application.training.memory.MemoryGameBoardPositionService;
import com.escontrela.lastmove.application.training.memory.MemoryGameChallenge;
import com.escontrela.lastmove.application.training.memory.MemoryGamePiece;
import com.escontrela.lastmove.application.training.memory.MemoryGameSnapshot;
import com.escontrela.lastmove.application.training.memory.MemoryGameOrchestrator;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.training.memory.MemoryGameState;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** UI-neutral projection of a memory-game snapshot, including hidden-square answers. */
public final class MemoryGameViewModel {
  private final MemoryGameOrchestrator orchestrator;
  private final MemoryGameBoardPositionService positions;
  private final Map<Square, MemoryGamePiece> answers = new HashMap<>();
  private MemoryGameSnapshot snapshot;
  private PositionSnapshot boardPosition;

  public MemoryGameViewModel(MemoryGameOrchestrator orchestrator, MemoryGameBoardPositionService positions) {
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    this.positions = Objects.requireNonNull(positions, "positions must not be null");
    orchestrator.observe(this::apply);
  }

  public void start() { orchestrator.start(); }
  public void restart() { orchestrator.restart(); }
  public void abandon() { orchestrator.abandon(); }

  /** Places a selected overlay piece only when the square is a current hidden target. */
  public void placePiece(Square square, PieceType type, PieceColor color) {
    if (!canAnswer(square)) return;
    answers.put(square, new MemoryGamePiece(square, type, color));
    renderGuessingPosition();
  }

  public void submit() {
    if (snapshot != null && snapshot.state() == MemoryGameState.GUESSING) {
      orchestrator.submitAnswer(Map.copyOf(answers));
    }
  }

  public Optional<MemoryGameSnapshot> snapshot() { return Optional.ofNullable(snapshot); }
  public Optional<PositionSnapshot> boardPosition() { return Optional.ofNullable(boardPosition); }
  public boolean canAnswer(Square square) {
    return snapshot != null && snapshot.state() == MemoryGameState.GUESSING
        && snapshot.feedback().isEmpty() && isHidden(square);
  }
  public boolean canSubmit() { return snapshot != null && snapshot.state() == MemoryGameState.GUESSING && !answers.isEmpty(); }
  public boolean finished() { return snapshot != null && snapshot.state() == MemoryGameState.FINISHED; }
  public boolean canRestart() { return snapshot != null && snapshot.canRestart(); }

  private void apply(MemoryGameSnapshot next) {
    snapshot = Objects.requireNonNull(next, "snapshot must not be null");
    if (next.challenge().isEmpty()) { answers.clear(); boardPosition = null; return; }
    MemoryGameChallenge challenge = next.challenge().orElseThrow();
    PositionSnapshot full = positions.snapshot(challenge.position());
    if (next.state() != MemoryGameState.GUESSING) answers.clear();
    boardPosition = full;
    if (next.state() == MemoryGameState.GUESSING) renderGuessingPosition();
  }

  private void renderGuessingPosition() {
    if (snapshot == null || snapshot.challenge().isEmpty()) return;
    MemoryGameChallenge challenge = snapshot.challenge().orElseThrow();
    PositionSnapshot full = positions.snapshot(challenge.position());
    var hiddenSquares = challenge.hiddenPieces().stream().map(MemoryGamePiece::square).toList();
    var visible = full.pieces().stream().filter(piece -> !hiddenSquares.contains(piece.square())).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    answers.values().forEach(answer -> visible.add(new PositionPiece(answer.square(), answer.type(), answer.color())));
    boardPosition = new PositionSnapshot(visible, full.activeColor(), full.castlingRights(), full.enPassantTarget(), full.halfmoveClock(), full.fullmoveNumber(), full.lastMove(), full.check(), full.mate(), full.stalemate());
  }

  private boolean isHidden(Square square) {
    return square != null && snapshot.challenge().orElseThrow().hiddenPieces().stream().anyMatch(piece -> piece.square().equals(square));
  }
}
