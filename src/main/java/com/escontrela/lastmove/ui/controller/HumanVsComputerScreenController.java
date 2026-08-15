package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.computer.ComputerGamePhase;
import com.escontrela.lastmove.application.computer.ComputerGameState;
import com.escontrela.lastmove.application.service.ComputerGameService;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.game.HumanVsComputerSetupOverlay;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.notation.MoveNotationNode;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Controller for the progressive-game screen configured as Human vs Computer.
 *
 * <p>It owns the active game identifier and renders application DTOs. The reusable board,
 * notation and promotion controls remain unaware of the computer engine and game repository.
 */
@Component
public final class HumanVsComputerScreenController implements UiScreenController {

  private static final double BOARD_MAX_SIZE = 720.0;

  private final UiFlowManager uiFlowManager;
  private final ComputerGameService computerGameService;
  private final ChessSoundService chessSoundService;
  private Timeline clockRefresh;

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MoveNotationControl moveNotation;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private HumanVsComputerSetupOverlay setupOverlay;
  @FXML private MessageBox resultMessageBox;
  @FXML private Label opponentPlayerLabel;
  @FXML private Label humanPlayerLabel;
  @FXML private Label opponentClockLabel;
  @FXML private Label humanClockLabel;
  @FXML private Label turnLabel;
  @FXML private Label statusLabel;
  @FXML private ProgressIndicator thinkingIndicator;
  @FXML private Button takeBackButton;
  @FXML private Button restartButton;
  @FXML private Button resignButton;

  private GameId activeGameId;
  private ComputerGameState renderedState;
  private BoardMoveInput pendingPromotionMove;
  private boolean screenVisible;
  private boolean resultShown;

  public HumanVsComputerScreenController(
      @Lazy UiFlowManager uiFlowManager,
      ComputerGameService computerGameService,
      ChessSoundService chessSoundService) {
    this.uiFlowManager = uiFlowManager;
    this.computerGameService = computerGameService;
    this.chessSoundService = chessSoundService;
  }

  @FXML
  public void initialize() {
    clockRefresh =
        new Timeline(
            new KeyFrame(javafx.util.Duration.millis(200), event -> refreshClockState()));
    clockRefresh.setCycleCount(Animation.INDEFINITE);
    root.getProperties().put("controller", this);
    chessSoundService.preload();
    chessBoard.setSoundService(chessSoundService);
    configureBoardInput();
    configurePromotionPicker();
    configureSetupOverlay();
    configureResultMessage();
    bindResponsiveBoardSize();
    showEmptyWorkspace();
  }

  @Override
  public void onShow() {
    screenVisible = true;
    if (activeGameId == null) {
      setupOverlay.show(computerGameService.availableEngines());
    }
  }

  @Override
  public void onHide() {
    screenVisible = false;
    clockRefresh.stop();
    closeActiveGame();
  }

  @FXML
  public void backToMain() {
    closeActiveGame();
    uiFlowManager.show(UiScreenId.MAIN);
  }

  @FXML
  public void takeBack() {
    if (activeGameId == null) {
      return;
    }
    try {
      resultMessageBox.hide();
      resultShown = false;
      applyState(computerGameService.takeBack(activeGameId));
    } catch (RuntimeException exception) {
      statusLabel.setText(exception.getMessage());
    }
  }

  @FXML
  public void resign() {
    if (activeGameId != null) {
      applyState(computerGameService.resign(activeGameId));
    }
  }

  /** Restarts the current game with the same opponent, colour and time control. */
  @FXML
  public void restart() {
    if (activeGameId == null) {
      return;
    }
    GameId previousGameId = activeGameId;
    activeGameId = null;
    resultMessageBox.hide();
    resultShown = false;
    showTransitionState("Restarting game…");
    final CompletionStage<ComputerGameState> restart;
    try {
      restart = computerGameService.restartGame(previousGameId);
    } catch (RuntimeException exception) {
      showRestartFailure(exception);
      return;
    }
    restart.whenComplete(
        (state, failure) ->
            Platform.runLater(
                () -> {
                  if (!screenVisible) {
                    if (state != null) {
                      computerGameService.closeGame(state.gameId());
                    }
                    return;
                  }
                  if (failure != null) {
                    showRestartFailure(failure);
                    return;
                  }
                  if (state.phase() == ComputerGamePhase.ENGINE_ERROR) {
                    computerGameService.closeGame(state.gameId());
                    showRestartFailure(
                        new IllegalStateException(
                            state.message().orElse("Unable to restart the engine")));
                    return;
                  }
                  activateGame(state);
                }));
  }

  private void configureSetupOverlay() {
    setupOverlay.setOnCancel(event -> backToMain());
    setupOverlay.setOnStartGame(event -> startGame(event.configuration()));
  }

  private void startGame(ComputerGameConfiguration configuration) {
    setupOverlay.setBusy(true);
    final CompletionStage<ComputerGameState> creation;
    try {
      creation = computerGameService.createGame(configuration);
    } catch (RuntimeException exception) {
      setupOverlay.showError(exception.getMessage());
      return;
    }
    creation.whenComplete(
        (state, failure) ->
            Platform.runLater(
                () -> {
                  if (!screenVisible) {
                    if (state != null) {
                      computerGameService.closeGame(state.gameId());
                    }
                    return;
                  }
                  if (failure != null) {
                    setupOverlay.showError(rootCauseMessage(failure));
                    return;
                  }
                  if (state.phase() == ComputerGamePhase.ENGINE_ERROR) {
                    computerGameService.closeGame(state.gameId());
                    setupOverlay.showError(state.message().orElse("Unable to start the engine"));
                    return;
                  }
                  setupOverlay.hide();
                  activateGame(state);
                }));
  }

  private void configureBoardInput() {
    chessBoard.setOnPromotionRequested(
        event -> {
          if (!canAcceptHumanInput()) {
            return;
          }
          pendingPromotionMove = event.getMoveInput();
          promotionPicker.showFor(event.getPromotingColor());
        });
    chessBoard.setOnMoveRequested(event -> submitHumanMove(event.getMoveInput()));
  }

  private void submitHumanMove(BoardMoveInput moveInput) {
    if (!canAcceptHumanInput()) {
      return;
    }
    GameId submittedGameId = activeGameId;
    CompletionStage<ComputerGameState> reply;
    try {
      reply =
          computerGameService.playHumanMove(
              submittedGameId,
              new MoveCommand(
                  moveInput.fromSquare(), moveInput.toSquare(), moveInput.promotionPiece()));
      applyState(computerGameService.state(submittedGameId));
    } catch (RuntimeException exception) {
      statusLabel.setText(exception.getMessage());
      return;
    }
    reply.whenComplete(
        (state, failure) ->
            Platform.runLater(
                () -> {
                  if (!screenVisible || !submittedGameId.equals(activeGameId)) {
                    return;
                  }
                  if (failure != null) {
                    statusLabel.setText(rootCauseMessage(failure));
                  } else {
                    applyState(state);
                  }
                }));
  }

  private void configurePromotionPicker() {
    promotionPicker.setOnPromotionSelected(
        event -> {
          if (pendingPromotionMove == null) {
            return;
          }
          BoardMoveInput completed = pendingPromotionMove.withPromotion(event.pieceType());
          pendingPromotionMove = null;
          chessBoard.handleBoardMoveInput(completed);
        });
    promotionPicker.setOnCancel(
        event -> {
          pendingPromotionMove = null;
          statusLabel.setText("Promotion cancelled");
        });
  }

  private void configureResultMessage() {
    resultMessageBox.setTitle("Game finished");
    resultMessageBox.setAcceptText("Play again");
    resultMessageBox.setCancelText("Back to tools");
    resultMessageBox.setOnAccept(
        event -> {
          resultMessageBox.hide();
          restart();
        });
    resultMessageBox.setOnCancel(event -> backToMain());
    resultMessageBox.setOnClose(event -> backToMain());
  }

  private void refreshClockState() {
    if (activeGameId == null || !screenVisible) {
      return;
    }
    try {
      applyState(computerGameService.state(activeGameId));
    } catch (RuntimeException exception) {
      clockRefresh.stop();
      statusLabel.setText(exception.getMessage());
    }
  }

  private void applyState(ComputerGameState state) {
    renderedState = Objects.requireNonNull(state, "state must not be null");
    chessBoard.renderPosition(state.position());
    boolean humanIsWhite = state.humanColor() == PieceColor.WHITE;
    humanPlayerLabel.setText(
        humanIsWhite ? state.whitePlayer().getName() : state.blackPlayer().getName());
    opponentPlayerLabel.setText(
        humanIsWhite ? state.blackPlayer().getName() : state.whitePlayer().getName());
    humanClockLabel.setText(
        formatClock(
            humanIsWhite ? state.clock().whiteRemaining() : state.clock().blackRemaining()));
    opponentClockLabel.setText(
        formatClock(
            humanIsWhite ? state.clock().blackRemaining() : state.clock().whiteRemaining()));
    turnLabel.setText(turnText(state));
    statusLabel.setText(state.message().orElseGet(() -> turnText(state)));
    takeBackButton.setDisable(!state.canTakeBack());
    restartButton.setDisable(false);
    resignButton.setDisable(state.result().isPresent());
    boolean engineThinking = state.phase() == ComputerGamePhase.ENGINE_THINKING;
    thinkingIndicator.setVisible(engineThinking);
    thinkingIndicator.setManaged(engineThinking);
    refreshNotation(state.moves());
    if (state.result().isPresent()) {
      clockRefresh.stop();
      showResult(state);
    }
  }

  private void refreshNotation(List<Ply> moves) {
    if (moves.isEmpty()) {
      moveNotation.setTree(List.of());
      moveNotation.setSelectedNodeId(null);
      return;
    }
    moveNotation.setTree(List.of(toNotationNode(moves, 0)));
    moveNotation.setSelectedNodeId(moves.getLast().id());
  }

  private MoveNotationNode toNotationNode(List<Ply> moves, int index) {
    Ply ply = moves.get(index);
    return new MoveNotationNode(
        new MoveNotationEntry(
            ply.id(),
            ply.moveNumber(),
            ply.movingColor() == PieceColor.WHITE,
            ply.move().san().getValue(),
            true),
        index + 1 < moves.size() ? List.of(toNotationNode(moves, index + 1)) : List.of());
  }

  private void showResult(ComputerGameState state) {
    if (resultShown) {
      return;
    }
    resultShown = true;
    String winner =
        state.result().orElseThrow() == GameResult.DRAW
            ? "Draw"
            : state.result().orElseThrow() == GameResult.WHITE_WINS
                ? state.whitePlayer().getName() + " wins"
                : state.blackPlayer().getName() + " wins";
    String reason =
        state.terminationReason().map(value -> value.name().toLowerCase().replace('_', ' ')).orElse("");
    resultMessageBox.setMessage(winner + (reason.isBlank() ? "" : " by " + reason) + ".");
    resultMessageBox.show();
  }

  private void showEmptyWorkspace() {
    renderedState = null;
    opponentPlayerLabel.setText("Computer");
    humanPlayerLabel.setText("Player");
    opponentClockLabel.setText("--:--");
    humanClockLabel.setText("--:--");
    turnLabel.setText("Configure a new game");
    statusLabel.setText("Choose an opponent, colour and time control");
    moveNotation.setTree(List.of());
    takeBackButton.setDisable(true);
    restartButton.setDisable(true);
    resignButton.setDisable(true);
    thinkingIndicator.setVisible(false);
    thinkingIndicator.setManaged(false);
  }

  private boolean canAcceptHumanInput() {
    return activeGameId != null && renderedState != null && renderedState.canMove();
  }

  private String turnText(ComputerGameState state) {
    return switch (state.phase()) {
      case STARTING -> "Starting engine…";
      case ENGINE_THINKING -> state.engine().displayName() + " is thinking…";
      case WAITING_FOR_HUMAN -> "Your turn";
      case FINISHED -> "Game finished";
      case ENGINE_ERROR -> "Computer engine error";
    };
  }

  private String formatClock(java.util.Optional<Duration> remaining) {
    if (remaining.isEmpty()) {
      return "∞";
    }
    long seconds = Math.max(0, remaining.orElseThrow().toSeconds());
    return "%02d:%02d".formatted(seconds / 60, seconds % 60);
  }

  private void closeActiveGame() {
    clockRefresh.stop();
    if (activeGameId != null) {
      computerGameService.closeGame(activeGameId);
      activeGameId = null;
    }
    renderedState = null;
  }

  private void activateGame(ComputerGameState state) {
    activeGameId = state.gameId();
    resultShown = false;
    chessBoard.setFlipped(state.humanColor() == PieceColor.BLACK);
    applyState(state);
    clockRefresh.play();
  }

  private void showTransitionState(String message) {
    clockRefresh.stop();
    turnLabel.setText(message);
    statusLabel.setText(message);
    takeBackButton.setDisable(true);
    restartButton.setDisable(true);
    resignButton.setDisable(true);
    thinkingIndicator.setVisible(true);
    thinkingIndicator.setManaged(true);
  }

  private void showRestartFailure(Throwable failure) {
    showEmptyWorkspace();
    statusLabel.setText(rootCauseMessage(failure));
    setupOverlay.show(computerGameService.availableEngines());
    setupOverlay.showError(rootCauseMessage(failure));
  }

  private void bindResponsiveBoardSize() {
    ChangeListener<Number> recompute = (observable, oldValue, newValue) -> updateBoardSize();
    boardHost.widthProperty().addListener(recompute);
    boardHost.heightProperty().addListener(recompute);
  }

  private void updateBoardSize() {
    double available = Math.min(boardHost.getWidth(), boardHost.getHeight());
    if (available <= 0) {
      return;
    }
    double side = Math.min(available, BOARD_MAX_SIZE);
    chessBoard.setPrefSize(side, side);
  }

  private String rootCauseMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }
}
