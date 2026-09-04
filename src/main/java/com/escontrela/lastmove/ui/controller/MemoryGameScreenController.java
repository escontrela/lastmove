package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.training.memory.MemoryGameBoardPositionService;
import com.escontrela.lastmove.application.training.memory.MemoryGameOrchestrator;
import com.escontrela.lastmove.application.training.memory.MemoryGameFeedback;
import com.escontrela.lastmove.application.training.memory.MemoryGameSnapshot;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.training.MemoryPiecePickerControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.model.MemoryGameViewModel;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import org.springframework.stereotype.Component;

/** Thin JavaFX controller for the memory-game screen; rules and orchestration stay outside UI. */
@Component
public final class MemoryGameScreenController implements UiScreenController {
  private final MemoryGameViewModel viewModel;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final ChessSoundService chessSoundService;
  @FXML private StackPane root;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MemoryPiecePickerControl piecePicker;
  @FXML private Label phaseLabel, globalClockLabel, memorizationClockLabel, scoreLabel, difficultyLabel, attemptLabel, statusLabel;
  @FXML private Label resultScoreLabel, resultVerdictLabel, resultDetailLabel;
  @FXML private VBox resultPanel;
  @FXML private ToolbarIconButton resetButton;
  @FXML private ToolbarIconButton soundToggleButton;
  @FXML private Button playAgainButton;
  private Square pendingSquare;
  private Timeline urgentClockPulse;
  private MemoryGameSnapshot previousSnapshot;
  private boolean soundEnabled = true;
  private boolean backgroundMusicPlaying;
  private boolean urgentClockSoundPlaying;
  private ChessSound backgroundSound;
  private static final double BACKGROUND_VOLUME = 0.18;
  private static final List<ChessSound> MEMORY_SOUNDS = List.of(
      ChessSound.MEMORY_GAME_OVER,
      ChessSound.MEMORY_GAME_COMPLETED,
      ChessSound.MEMORY_CLOCK_URGENT,
      ChessSound.MEMORY_INCORRECT_PIECE,
      ChessSound.MEMORY_CORRECT_PIECE,
      ChessSound.MEMORY_PIECES_DISAPPEAR,
      ChessSound.MEMORY_NEW_POSITION,
      ChessSound.MEMORY_BACKGROUND,
      ChessSound.MEMORY_BACKGROUND_WIND,
      ChessSound.MEMORY_BACKGROUND_RAIN);
  private static final List<ChessSound> MEMORY_BACKGROUND_SOUNDS = List.of(
      ChessSound.MEMORY_BACKGROUND,
      ChessSound.MEMORY_BACKGROUND_WIND,
      ChessSound.MEMORY_BACKGROUND_RAIN);

  public MemoryGameScreenController(
      MemoryGameOrchestrator orchestrator,
      MemoryGameBoardPositionService positions,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      ChessSoundService chessSoundService) {
    this.viewModel = new MemoryGameViewModel(orchestrator, positions);
    this.boardAppearancePreferencesService = boardAppearancePreferencesService;
    this.chessSoundService = Objects.requireNonNull(chessSoundService, "chessSoundService must not be null");
    orchestrator.observe(this::refresh);
  }

  @FXML public void initialize() {
    root.getProperties().put("controller", this);
    chessSoundService.preload();
    updateSoundToggleButton();
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    chessBoard.appearancePresetProperty().bind(
        boardAppearancePreferencesService.boardAppearancePresetProperty());
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

  @Override public void onShow() {
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.start();
  }
  @Override public void onHide() {
    piecePicker.hide();
    pendingSquare = null;
    stopUrgentClockPulse();
    stopMemorySounds();
    backgroundMusicPlaying = false;
    urgentClockSoundPlaying = false;
    previousSnapshot = null;
    viewModel.abandon();
  }

  @FXML public void playAgain() {
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.restart();
  }
  @FXML public void resetSession() {
    piecePicker.hide();
    pendingSquare = null;
    selectBackgroundSound();
    startBackgroundMusic();
    viewModel.start();
  }

  @FXML public void toggleSound() {
    soundEnabled = !soundEnabled;
    if (soundEnabled) {
      startBackgroundMusic();
      if (isUrgent(previousSnapshot)) startUrgentClockSound();
    } else {
      stopMemorySounds();
      backgroundMusicPlaying = false;
      urgentClockSoundPlaying = false;
    }
    updateSoundToggleButton();
  }

  private void requestPieceFor(Square square) {
    if (!viewModel.canAnswer(square)) return;
    pendingSquare = square;
    piecePicker.showPicker();
  }

  private void refresh(MemoryGameSnapshot snapshot) {
    handleSoundEffects(snapshot);
    if (snapshot.state() == com.escontrela.lastmove.domain.training.memory.MemoryGameState.FINISHED) {
      stopBackgroundMusic();
    }
    viewModel.boardPosition().ifPresent(chessBoard::renderPosition);
    var correct = snapshot.feedback().stream().filter(MemoryGameFeedback::correct).map(MemoryGameFeedback::square).collect(Collectors.toSet());
    var incorrect = snapshot.feedback().stream().filter(feedback -> !feedback.correct()).map(MemoryGameFeedback::square).collect(Collectors.toSet());
    if (correct.isEmpty() && incorrect.isEmpty()) chessBoard.clearFeedback(); else chessBoard.showFeedback(correct, incorrect);
    phaseLabel.setText(phaseText(snapshot));
    globalClockLabel.setText(format(snapshot.remainingTime()));
    updateUrgentClock(snapshot.remainingTime());
    memorizationClockLabel.setText(format(snapshot.memorizationRemaining()));
    scoreLabel.setText("%d/%d".formatted(snapshot.score(), snapshot.maxPossibleScore()));
    difficultyLabel.setText(snapshot.difficulty().map(value -> value.hiddenPieceCount() + " hidden piece(s)").orElse("—"));
    attemptLabel.setText("Attempt " + snapshot.attempt());
    chessBoard.setEditorMode(snapshot.state() == com.escontrela.lastmove.domain.training.memory.MemoryGameState.GUESSING);
    if (viewModel.finished()) { piecePicker.hide(); pendingSquare = null; }
    statusLabel.setText(statusText(snapshot));
    refreshResult(snapshot);
  }

  private void handleSoundEffects(MemoryGameSnapshot snapshot) {
    MemoryGameSnapshot previous = previousSnapshot;
    if (previous != null) {
      if (!previous.feedback().equals(snapshot.feedback()) && !snapshot.feedback().isEmpty()) {
        playMemorySound(snapshot.feedback().getFirst().correct()
            ? ChessSound.MEMORY_CORRECT_PIECE
            : ChessSound.MEMORY_INCORRECT_PIECE);
      }
      if (previous.showingCompletePosition() && !snapshot.showingCompletePosition()) {
        playMemorySound(ChessSound.MEMORY_PIECES_DISAPPEAR);
      }
      if (previous.challenge().isPresent()
          && snapshot.challenge().isPresent()
          && !previous.challenge().orElseThrow().equals(snapshot.challenge().orElseThrow())) {
        playMemorySound(ChessSound.MEMORY_NEW_POSITION);
      }
      if (previous.state() != com.escontrela.lastmove.domain.training.memory.MemoryGameState.FINISHED
          && snapshot.state() == com.escontrela.lastmove.domain.training.memory.MemoryGameState.FINISHED) {
        playMemorySound(snapshot.successful()
            ? ChessSound.MEMORY_GAME_COMPLETED
            : ChessSound.MEMORY_GAME_OVER);
      }
    }
    previousSnapshot = snapshot;
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

  private void updateUrgentClock(Duration remaining) {
    boolean urgent = remaining.compareTo(Duration.ZERO) > 0
        && remaining.compareTo(Duration.ofSeconds(30)) < 0;
    globalClockLabel.getStyleClass().remove("memory-global-clock-urgent");
    if (!urgent) {
      stopUrgentClockPulse();
      stopUrgentClockSound();
      globalClockLabel.setOpacity(1.0);
      return;
    }
    globalClockLabel.getStyleClass().add("memory-global-clock-urgent");
    if (urgentClockPulse == null) {
      urgentClockPulse = new Timeline(
          new KeyFrame(javafx.util.Duration.ZERO,
              new KeyValue(globalClockLabel.opacityProperty(), 1.0)),
          new KeyFrame(javafx.util.Duration.millis(700),
              new KeyValue(globalClockLabel.opacityProperty(), 0.45)),
          new KeyFrame(javafx.util.Duration.millis(1400),
              new KeyValue(globalClockLabel.opacityProperty(), 1.0)));
      urgentClockPulse.setCycleCount(Animation.INDEFINITE);
      urgentClockPulse.play();
    }
    startUrgentClockSound();
  }

  private void stopUrgentClockPulse() {
    if (urgentClockPulse != null) {
      urgentClockPulse.stop();
      urgentClockPulse = null;
    }
  }

  private void stopUrgentClockSound() {
    if (urgentClockSoundPlaying) {
      chessSoundService.stop(ChessSound.MEMORY_CLOCK_URGENT);
      urgentClockSoundPlaying = false;
    }
  }

  private void startBackgroundMusic() {
    if (!soundEnabled || backgroundMusicPlaying || backgroundSound == null) return;
    chessSoundService.playLoop(backgroundSound, BACKGROUND_VOLUME);
    backgroundMusicPlaying = true;
  }

  private void stopBackgroundMusic() {
    if (backgroundMusicPlaying && backgroundSound != null) {
      chessSoundService.stop(backgroundSound);
    }
    backgroundMusicPlaying = false;
  }

  private void selectBackgroundSound() {
    backgroundSound = MEMORY_BACKGROUND_SOUNDS.get(
        ThreadLocalRandom.current().nextInt(MEMORY_BACKGROUND_SOUNDS.size()));
  }

  private void startUrgentClockSound() {
    if (!soundEnabled || urgentClockSoundPlaying) return;
    chessSoundService.playLoop(ChessSound.MEMORY_CLOCK_URGENT);
    urgentClockSoundPlaying = true;
  }

  private void playMemorySound(ChessSound sound) {
    if (soundEnabled) chessSoundService.play(sound);
  }

  private void stopMemorySounds() {
    MEMORY_SOUNDS.forEach(chessSoundService::stop);
  }

  private void updateSoundToggleButton() {
    if (soundToggleButton == null) return;
    String icon = soundEnabled ? "stop" : "play_arrow";
    soundToggleButton.setText(soundEnabled ? "Sound off" : "Sound on");
    soundToggleButton.setAccessibleText(soundEnabled ? "Turn sound off" : "Turn sound on");
    soundToggleButton.setTooltipText(soundEnabled ? "Turn sound off" : "Turn sound on");
    soundToggleButton.setLightIconResource("/images/" + icon + "_35dp_000000.png");
    soundToggleButton.setDarkIconResource("/images/" + icon + "_35dp_FFFFFF.png");
  }

  private static boolean isUrgent(MemoryGameSnapshot snapshot) {
    return snapshot != null
        && snapshot.remainingTime().compareTo(Duration.ZERO) > 0
        && snapshot.remainingTime().compareTo(Duration.ofSeconds(30)) < 0;
  }
}
