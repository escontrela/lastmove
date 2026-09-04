package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.training.memory.MemoryGameBoardPositionService;
import com.escontrela.lastmove.application.training.memory.MemoryGameOrchestrator;
import com.escontrela.lastmove.application.training.memory.MemoryGameFeedback;
import com.escontrela.lastmove.application.training.memory.MemoryGameSnapshot;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.training.MemoryPiecePickerControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.model.MemoryGameViewModel;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import java.time.Duration;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

/** Thin JavaFX controller for the memory-game screen; rules and orchestration stay outside UI. */
@Component
public final class MemoryGameScreenController implements UiScreenController {
  private final MemoryGameViewModel viewModel;
  @FXML private StackPane root;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MemoryPiecePickerControl piecePicker;
  @FXML private Label phaseLabel, globalClockLabel, memorizationClockLabel, scoreLabel, difficultyLabel, attemptLabel, statusLabel;
  @FXML private Label resultScoreLabel, resultVerdictLabel, resultDetailLabel;
  @FXML private VBox resultPanel;
  @FXML private ToolbarIconButton resetButton;
  @FXML private Button playAgainButton;
  private Square pendingSquare;

  public MemoryGameScreenController(
      MemoryGameOrchestrator orchestrator,
      MemoryGameBoardPositionService positions) {
    this.viewModel = new MemoryGameViewModel(orchestrator, positions);
    orchestrator.observe(this::refresh);
  }

  @FXML public void initialize() {
    root.getProperties().put("controller", this);
    chessBoard.setEditorMode(false);
    chessBoard.setOnEditorSquareRequested(event -> requestPieceFor(event.getSquare()));
    chessBoard.setOnEditorSecondarySquareRequested(event -> requestPieceFor(event.getSquare()));
    piecePicker.setOnPieceSelected(event -> {
      if (pendingSquare != null) {
        viewModel.placePiece(pendingSquare, event.pieceType(), event.pieceColor());
      }
      pendingSquare = null;
    });
    piecePicker.setOnCancel(event -> pendingSquare = null);
    phaseLabel.setAccessibleText("Training phase");
    globalClockLabel.setAccessibleText("Global time remaining");
    memorizationClockLabel.setAccessibleText("Memorization time remaining");
    scoreLabel.setAccessibleText("Current score");
    attemptLabel.setAccessibleText("Current attempt");
  }

  @Override public void onShow() { viewModel.start(); }
  @Override public void onHide() { piecePicker.hide(); pendingSquare = null; viewModel.abandon(); }

  @FXML public void playAgain() { viewModel.restart(); }
  @FXML public void resetSession() { piecePicker.hide(); pendingSquare = null; viewModel.start(); }

  private void requestPieceFor(Square square) {
    if (!viewModel.canAnswer(square)) return;
    pendingSquare = square;
    piecePicker.showPicker();
  }

  private void refresh(MemoryGameSnapshot snapshot) {
    viewModel.boardPosition().ifPresent(chessBoard::renderPosition);
    var correct = snapshot.feedback().stream().filter(MemoryGameFeedback::correct).map(MemoryGameFeedback::square).collect(Collectors.toSet());
    var incorrect = snapshot.feedback().stream().filter(feedback -> !feedback.correct()).map(MemoryGameFeedback::square).collect(Collectors.toSet());
    if (correct.isEmpty() && incorrect.isEmpty()) chessBoard.clearFeedback(); else chessBoard.showFeedback(correct, incorrect);
    phaseLabel.setText(phaseText(snapshot));
    globalClockLabel.setText(format(snapshot.remainingTime()));
    memorizationClockLabel.setText(format(snapshot.memorizationRemaining()));
    scoreLabel.setText("%d/%d".formatted(snapshot.score(), snapshot.maxPossibleScore()));
    difficultyLabel.setText(snapshot.difficulty().map(value -> value.hiddenPieceCount() + " hidden piece(s)").orElse("—"));
    attemptLabel.setText("Attempt " + snapshot.attempt());
    chessBoard.setEditorMode(snapshot.state() == com.escontrela.lastmove.domain.training.memory.MemoryGameState.GUESSING
        && snapshot.feedback().isEmpty());
    if (!snapshot.feedback().isEmpty() || viewModel.finished()) { piecePicker.hide(); pendingSquare = null; }
    statusLabel.setText(statusText(snapshot));
    refreshResult(snapshot);
  }

  /** Shows the final result card with a pass/fail verdict that does not rely on colour alone. */
  private void refreshResult(MemoryGameSnapshot snapshot) {
    boolean finished = viewModel.finished();
    resultPanel.setManaged(finished);
    resultPanel.setVisible(finished);
    if (!finished) return;
    int percent = (int) Math.round(snapshot.successRate() * 100);
    boolean passed = snapshot.successful();
    String verdictClass = passed ? "score-high" : "score-low";
    resultScoreLabel.setText(percent + "%");
    resultScoreLabel.setAccessibleText("Success rate " + percent + " percent");
    resultScoreLabel.getStyleClass().removeAll("score-high", "score-low");
    resultScoreLabel.getStyleClass().add(verdictClass);
    resultVerdictLabel.setText(passed ? "✓ Passed" : "✗ Not passed");
    resultVerdictLabel.getStyleClass().removeAll("score-high", "score-low");
    resultVerdictLabel.getStyleClass().add(verdictClass);
    resultDetailLabel.setText("Points: %d of %d".formatted(snapshot.score(), snapshot.maxPossibleScore()));
    playAgainButton.setManaged(viewModel.canRestart());
    playAgainButton.setVisible(viewModel.canRestart());
  }

  private static String phaseText(MemoryGameSnapshot snapshot) {
    if (snapshot.emptySource()) return "No positions available";
    if (!snapshot.feedback().isEmpty()) return "Review answer";
    return switch (snapshot.state()) {
      case READY -> "Ready";
      case MEMORIZING -> "Memorize";
      case GUESSING -> "Rebuild the position";
      case FINISHED -> "Finished";
    };
  }

  private static String statusText(MemoryGameSnapshot snapshot) {
    if (snapshot.emptySource()) return "No playable positions available. Return to Home.";
    if (!snapshot.feedback().isEmpty()) {
      return "Review your answer. An incorrect answer reveals the correct piece blinking for two seconds.";
    }
    return switch (snapshot.state()) {
      case MEMORIZING -> "Memorize the complete position before pieces disappear.";
      case GUESSING -> {
        int total = snapshot.challenge().map(challenge -> challenge.hiddenPieces().size()).orElse(0);
        yield "Select any empty square, then choose the piece to place there. Pieces: %d/%d."
            .formatted(snapshot.resolvedPieces().size(), total);
      }
      case FINISHED -> "Session complete.";
      case READY -> "Preparing training session.";
    };
  }

  private static String format(Duration duration) {
    long seconds = duration.isZero() || duration.isNegative() ? 0 : duration.minusNanos(1).toSeconds() + 1;
    return "%d:%02d".formatted(seconds / 60, seconds % 60);
  }
}
