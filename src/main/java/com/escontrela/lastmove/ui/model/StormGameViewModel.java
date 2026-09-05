package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.application.training.storm.StormGameOrchestrator;
import com.escontrela.lastmove.application.training.storm.StormGameSnapshot;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.training.storm.StormGameState;
import java.util.Objects;
import java.util.Optional;

/** UI-neutral projection of the Storm session and its current tactical position. */
public final class StormGameViewModel {
  private final StormGameOrchestrator orchestrator;
  private StormGameSnapshot snapshot;
  private PositionSnapshot boardPosition;

  public StormGameViewModel(StormGameOrchestrator orchestrator) {
    this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    orchestrator.observe(this::apply);
  }

  public void start() {
    orchestrator.start();
  }

  public void restart() {
    orchestrator.restart();
  }

  public void abandon() {
    orchestrator.abandon();
  }

  public void submit(BoardMoveInput input) {
    orchestrator.submitMove(
        new MoveCommand(input.fromSquare(), input.toSquare(), input.promotionPiece()));
  }

  public void requestHint() {
    orchestrator.requestHint();
  }

  public Optional<StormGameSnapshot> snapshot() {
    return Optional.ofNullable(snapshot);
  }

  public Optional<PositionSnapshot> boardPosition() {
    return Optional.ofNullable(boardPosition);
  }

  public boolean canMove() {
    return snapshot != null
        && snapshot.state() == StormGameState.RUNNING
        && snapshot.challenge().isPresent()
        && snapshot.workspace().map(workspace -> !workspace.solved()).orElse(true);
  }

  public boolean finished() {
    return snapshot != null && snapshot.state() == StormGameState.FINISHED;
  }

  public boolean isBlackToSolve() {
    return snapshot != null
        && snapshot.challenge().map(c -> c.solverColor() == PieceColor.BLACK).orElse(false);
  }

  private void apply(StormGameSnapshot next) {

    snapshot = next;
    boardPosition =
        next.workspace()
            .map(workspace -> workspace.position())
            .orElseGet(() -> next.challenge().map(challenge -> challenge.position()).orElse(null));
  }
}
