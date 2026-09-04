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
import java.util.Objects;
import java.util.Optional;

/** UI-neutral projection of a memory-game snapshot, including hidden-square answers. */
public final class MemoryGameViewModel {
  private final MemoryGameOrchestrator orchestrator;
  private final MemoryGameBoardPositionService positions;
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

  /** Places and immediately evaluates a selected overlay piece on any empty square. */
  public void placePiece(Square square, PieceType type, PieceColor color) {
    if (!canAnswer(square)) return;
    orchestrator.submitPiece(new MemoryGamePiece(square, type, color));
  }

  public Optional<MemoryGameSnapshot> snapshot() { return Optional.ofNullable(snapshot); }
  public Optional<PositionSnapshot> boardPosition() { return Optional.ofNullable(boardPosition); }
  public boolean canAnswer(Square square) {
    return snapshot != null && snapshot.state() == MemoryGameState.GUESSING
        && snapshot.feedback().isEmpty() && !isOccupied(square);
  }
  public boolean finished() { return snapshot != null && snapshot.state() == MemoryGameState.FINISHED; }
  public boolean canRestart() { return snapshot != null && snapshot.canRestart(); }

  private void apply(MemoryGameSnapshot next) {
    snapshot = Objects.requireNonNull(next, "snapshot must not be null");
    if (next.challenge().isEmpty()) { boardPosition = null; return; }
    MemoryGameChallenge challenge = next.challenge().orElseThrow();
    PositionSnapshot full = positions.snapshot(challenge.position());
    boardPosition = full;
    if (next.state() == MemoryGameState.GUESSING) renderGuessingPosition();
  }

  private void renderGuessingPosition() {
    if (snapshot == null || snapshot.challenge().isEmpty()) return;
    MemoryGameChallenge challenge = snapshot.challenge().orElseThrow();
    PositionSnapshot full = positions.snapshot(challenge.position());
    var hiddenSquares = challenge.hiddenPieces().stream().map(MemoryGamePiece::square).toList();
    var visible = full.pieces().stream().filter(piece -> !hiddenSquares.contains(piece.square())).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    snapshot.resolvedPieces().forEach(answer -> visible.add(new PositionPiece(answer.square(), answer.type(), answer.color())));
    boardPosition = new PositionSnapshot(visible, full.activeColor(), full.castlingRights(), full.enPassantTarget(), full.halfmoveClock(), full.fullmoveNumber(), full.lastMove(), full.check(), full.mate(), full.stalemate());
  }

  private boolean isOccupied(Square square) {
    return square == null || boardPosition == null
        || boardPosition.pieces().stream().anyMatch(piece -> piece.square().equals(square));
  }
}
