package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.training.storm.StormGameFeedback;
import com.escontrela.lastmove.application.training.storm.StormGameOrchestrator;
import com.escontrela.lastmove.application.training.storm.StormGameSnapshot;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.model.StormGameViewModel;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import org.springframework.stereotype.Component;

/** Thin JavaFX adapter for Training Storm. */
@Component
public final class StormGameScreenController implements UiScreenController {
  @FXML private StackPane root;
  @FXML private ChessBoardControl chessBoard;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private Label phaseLabel, globalClockLabel, scoreLabel, puzzleLabel, difficultyLabel, solverColorLabel, statusLabel;
  @FXML private Circle solverColorIndicator;
  @FXML private Label resultScoreLabel, resultVerdictLabel, resultDetailLabel;
  @FXML private VBox resultPanel;
  @FXML private Button hintButton, playAgainButton;
  @FXML private ToolbarIconButton soundToggleButton;
  @FXML private MessageBox exitConfirmation;
  private final StormGameViewModel viewModel;
  private final BoardAppearancePreferencesService appearance;
  private final ChessSoundService sound;
  private BoardMoveInput pendingPromotion;
  private boolean soundEnabled = true;
  private boolean backgroundMusicPlaying;
  private boolean urgentClockSoundPlaying;
  private ChessSound backgroundSound;
  private Timeline urgentClockPulse;
  private StormGameSnapshot previousSnapshot;
  private StormGameSnapshot currentSnapshot;

  public StormGameScreenController(
      StormGameOrchestrator orchestrator,
      BoardAppearancePreferencesService appearance,
      ChessSoundService sound) {
    viewModel = new StormGameViewModel(orchestrator);
    this.appearance = appearance;
    this.sound = sound;
    orchestrator.observe(this::refresh);
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    sound.preload();
    chessBoard.setSoundService(sound);
    chessBoard.setEditorMode(false);
    chessBoard.visualEffectsEnabledProperty().bind(appearance.boardVisualEffectsEnabledProperty());
    chessBoard.appearancePresetProperty().bind(appearance.boardAppearancePresetProperty());
    chessBoard.setOnMoveRequested(event -> submit(event.getMoveInput()));
    chessBoard.setOnPromotionRequested(
        event -> {
          pendingPromotion = event.getMoveInput();
          promotionPicker.showFor(event.getPromotingColor());
        });
    promotionPicker.setOnPromotionSelected(
        event -> {
          if (pendingPromotion != null) {
            BoardMoveInput move = pendingPromotion.withPromotion(event.pieceType());
            pendingPromotion = null;
            chessBoard.handleBoardMoveInput(move);
          }
        });
    promotionPicker.setOnCancel(event -> pendingPromotion = null);
  }

  @Override
  public void onShow() {
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.start();
  }

  @Override
  public void requestExit(Runnable exitAction) {
    exitConfirmation.setTitle("Leave Training Storm?");
    exitConfirmation.setMessage("If you leave now, you will lose this puzzle. Continue?");
    exitConfirmation.setAcceptText("Continue");
    exitConfirmation.setCancelText("Cancel");
    exitConfirmation.setOnAccept(event -> {
      exitConfirmation.hide();
      exitAction.run();
    });
    exitConfirmation.setOnCancel(event -> exitConfirmation.hide());
    exitConfirmation.setOnClose(event -> exitConfirmation.hide());
    exitConfirmation.show();
  }

  @Override
  public void onHide() {
    promotionPicker.hide();
    pendingPromotion = null;
    stopStormSounds();
    previousSnapshot = null;
    viewModel.abandon();
  }

  @FXML
  public void resetSession() {
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.start();
  }

  @FXML
  public void playAgain() {
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.restart();
  }

  @FXML
  public void onHint() {
    viewModel.requestHint();
  }

  @FXML
  public void toggleSound() {
    soundEnabled = !soundEnabled;
    if (soundEnabled) startBackgroundMusic();
    else {
      stopStormSounds();
      backgroundMusicPlaying = false;
    }
    soundToggleButton.setText(soundEnabled ? "Sound off" : "Sound on");
    soundToggleButton.setAccessibleText(soundEnabled ? "Turn sound off" : "Turn sound on");
  }

  private void submit(BoardMoveInput input) {
    if (canAcceptMove(currentSnapshot)) {
      chessBoard.clearHintSquares();
      viewModel.submit(input);
    }
  }

  private void refresh(StormGameSnapshot snapshot) {
    currentSnapshot = snapshot;
    handleSoundEffects(snapshot);
    viewModel.boardPosition().ifPresent(chessBoard::renderPosition);
    chessBoard.setHintSquares(
        snapshot.feedback().flatMap(StormGameFeedback::hintSquare).orElse(null),
        snapshot.feedback().flatMap(StormGameFeedback::hintTargetSquare).orElse(null));
    applyPuzzleFeedback(snapshot);
    snapshot
        .challenge()
        .ifPresent(
            challenge -> chessBoard.setFlipped(challenge.solverColor().name().equals("BLACK")));
    phaseLabel.setText(
        snapshot.emptySource()
            ? "No puzzles available"
            : snapshot.state()
                    == com.escontrela.lastmove.domain.training.storm.StormGameState.RUNNING
                ? "Solve the tactic"
                : "Session finished");
    globalClockLabel.setText(format(snapshot.remainingTime()));
    scoreLabel.setText("%d/%d".formatted(snapshot.correctAnswers(), snapshot.finalizedPuzzles()));
    puzzleLabel.setText(snapshot.challenge().map(c -> c.title()).orElse("—"));
    snapshot.challenge().ifPresentOrElse(
        challenge -> difficultyLabel.setText(
            "Level: %s".formatted(challenge.difficulty().tagName())),
        () -> difficultyLabel.setText("Level: —"));
    snapshot.challenge().ifPresentOrElse(challenge -> {
      boolean white = challenge.solverColor().name().equals("WHITE");
      solverColorIndicator.setVisible(true);
      solverColorIndicator.setManaged(true);
      solverColorIndicator.setFill(white ? Color.WHITE : Color.web("#1b1f26"));
      solverColorIndicator.setStroke(Color.web("#69727d"));
      solverColorLabel.setText(white ? "You play White" : "You play Black");
    }, () -> {
      solverColorIndicator.setVisible(false);
      solverColorIndicator.setManaged(false);
      solverColorLabel.setText("Color: —");
    });
    statusLabel.setText(status(snapshot));
    boolean acceptsMoves = canAcceptMove(snapshot);
    chessBoard.setDisable(!acceptsMoves);
    hintButton.setDisable(!acceptsMoves);
    hintButton.setText(snapshot.workspace().map(workspace -> workspace.hintCount() > 0)
        .orElse(false) ? "Reveal move" : "Hint");
    boolean finished = viewModel.finished();
    resultPanel.setManaged(finished);
    resultPanel.setVisible(finished);
    if (finished) {
      int percent = (int) Math.round(snapshot.percentage());
      boolean ok = snapshot.successful();
      resultScoreLabel.setText(percent + "%");
      resultScoreLabel.getStyleClass().removeAll("storm-ok", "storm-ko");
      resultScoreLabel.getStyleClass().add(ok ? "storm-ok" : "storm-ko");
      resultVerdictLabel.setText(ok ? "✓ OK" : "✗ KO");
      resultVerdictLabel.getStyleClass().removeAll("storm-ok", "storm-ko");
      resultVerdictLabel.getStyleClass().add(ok ? "storm-ok" : "storm-ko");
      resultDetailLabel.setText(
          "Correct: %d · Completed puzzles: %d · Percentage: %d%%"
              .formatted(snapshot.correctAnswers(), snapshot.finalizedPuzzles(), percent));
      playAgainButton.setVisible(true);
      playAgainButton.setManaged(true);
    }
  }

  /** Reuses the board's transient green answer effect for a completed Storm line. */
  private void applyPuzzleFeedback(StormGameSnapshot snapshot) {
    var completedMove = snapshot.feedback()
        .filter(feedback -> feedback.correct() && feedback.solved())
        .flatMap(StormGameFeedback::workspace)
        .flatMap(workspace -> workspace.position().lastMove());
    if (completedMove.isPresent()) {
      chessBoard.showFeedback(Set.of(completedMove.orElseThrow().to()), Set.of());
    } else {
      chessBoard.clearFeedback();
    }
  }

  static boolean canAcceptMove(StormGameSnapshot snapshot) {
    return snapshot != null
        && snapshot.state() == com.escontrela.lastmove.domain.training.storm.StormGameState.RUNNING
        && snapshot.challenge().isPresent()
        && snapshot.workspace().map(workspace -> !workspace.solved()).orElse(true);
  }

  private void handleSoundEffects(StormGameSnapshot next) {
    StormGameSnapshot previous = previousSnapshot;
    if (isNewPuzzle(previous, next)) {
      playStormSound(ChessSound.MEMORY_NEW_POSITION);
    }
    if (previous != null) {
      if (!previous.feedback().equals(next.feedback()) && next.feedback().isPresent()) {
        StormGameFeedback feedback = next.feedback().orElseThrow();
        if (!feedback.hintUsed())
          playStormSound(
              feedback.correct()
                  ? ChessSound.MEMORY_CORRECT_PIECE
                  : ChessSound.MEMORY_INCORRECT_PIECE);
      }
      if (previous.state() != com.escontrela.lastmove.domain.training.storm.StormGameState.FINISHED
          && next.state()
              == com.escontrela.lastmove.domain.training.storm.StormGameState.FINISHED) {
        playStormSound(
            next.successful() ? ChessSound.MEMORY_GAME_COMPLETED : ChessSound.MEMORY_GAME_OVER);
        stopBackgroundMusic();
      }
    }
    updateUrgentClock(next.remainingTime());
    previousSnapshot = next;
  }

  private static boolean isNewPuzzle(StormGameSnapshot previous, StormGameSnapshot next) {
    if (next.challenge().isEmpty()) return false;
    if (previous == null || previous.challenge().isEmpty()) return true;
    if (!previous.challenge().orElseThrow().exerciseId().equals(next.challenge().orElseThrow().exerciseId())) {
      return true;
    }
    return previous.feedback().map(StormGameFeedback::solved).orElse(false)
        && next.feedback().isEmpty();
  }

  private void updateUrgentClock(Duration remaining) {
    boolean urgent =
        remaining.compareTo(Duration.ZERO) > 0 && remaining.compareTo(Duration.ofSeconds(30)) < 0;
    globalClockLabel.getStyleClass().remove("storm-global-clock-urgent");
    if (!urgent) {
      stopUrgentClockPulse();
      stopUrgentClockSound();
      return;
    }
    globalClockLabel.getStyleClass().add("storm-global-clock-urgent");
    if (urgentClockPulse == null) {
      urgentClockPulse =
          new Timeline(
              new KeyFrame(
                  javafx.util.Duration.ZERO, new KeyValue(globalClockLabel.opacityProperty(), 1.0)),
              new KeyFrame(
                  javafx.util.Duration.millis(700),
                  new KeyValue(globalClockLabel.opacityProperty(), .45)),
              new KeyFrame(
                  javafx.util.Duration.millis(1400),
                  new KeyValue(globalClockLabel.opacityProperty(), 1.0)));
      urgentClockPulse.setCycleCount(Animation.INDEFINITE);
      urgentClockPulse.play();
    }
    if (soundEnabled && !urgentClockSoundPlaying) {
      sound.playLoop(ChessSound.MEMORY_CLOCK_URGENT);
      urgentClockSoundPlaying = true;
    }
  }

  private void stopUrgentClockPulse() {
    if (urgentClockPulse != null) {
      urgentClockPulse.stop();
      urgentClockPulse = null;
      globalClockLabel.setOpacity(1.0);
    }
  }

  private void stopUrgentClockSound() {
    if (urgentClockSoundPlaying) {
      sound.stop(ChessSound.MEMORY_CLOCK_URGENT);
      urgentClockSoundPlaying = false;
    }
  }

  private void selectBackgroundSound() {
    backgroundSound =
        List.of(
                ChessSound.MEMORY_BACKGROUND,
                ChessSound.MEMORY_BACKGROUND_WIND,
                ChessSound.MEMORY_BACKGROUND_RAIN)
            .get(ThreadLocalRandom.current().nextInt(3));
  }

  private void startBackgroundMusic() {
    if (soundEnabled && !backgroundMusicPlaying && backgroundSound != null) {
      sound.playLoop(backgroundSound, .18);
      backgroundMusicPlaying = true;
    }
  }

  private void stopBackgroundMusic() {
    if (backgroundSound != null) sound.stop(backgroundSound);
    backgroundMusicPlaying = false;
  }

  private void playStormSound(ChessSound effect) {
    if (soundEnabled) sound.play(effect);
  }

  private void stopStormSounds() {
    // Only the ambient and urgent-clock clips are loops. Stopping every preloaded one-shot
    // AudioClip on the JavaFX thread caused a noticeable UI stall when muting Storm.
    stopBackgroundMusic();
    stopUrgentClockPulse();
    stopUrgentClockSound();
  }

  private static String status(StormGameSnapshot snapshot) {
    if (snapshot.emptySource()) return "No playable tactical puzzles are available.";
    if (snapshot.workspace().map(w -> w.solved()).orElse(false))
      return snapshot.feedback().map(StormGameFeedback::failed).orElse(false)
          ? "Puzzle solved, but marked as failed."
          : "Puzzle solved correctly: success.";
    if (snapshot.feedback().map(feedback -> feedback.hintTargetSquare().isPresent()).orElse(false))
      return "Source and target highlighted; play the move yourself. The puzzle is marked as failed.";
    if (snapshot
        .workspace()
        .map(w -> w.hintCount() > 0 || w.attemptedMoves() > w.correctMoves())
        .orElse(false)) return "Puzzle already failed; continue until you solve it.";
    return snapshot.state().name().equals("FINISHED") ? "Session complete." : "Find the best move.";
  }

  private static String format(Duration duration) {
    long seconds =
        duration.isZero() || duration.isNegative() ? 0 : duration.minusNanos(1).toSeconds() + 1;
    return "%d:%02d".formatted(seconds / 60, seconds % 60);
  }
}
