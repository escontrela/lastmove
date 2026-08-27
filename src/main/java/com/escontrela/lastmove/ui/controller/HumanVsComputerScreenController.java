package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerGamePhase;
import com.escontrela.lastmove.application.computer.ComputerGameState;
import com.escontrela.lastmove.application.service.ComputerGameService;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.OpeningPracticeService;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameId;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.game.CapturedPiecesControl;
import com.escontrela.lastmove.ui.component.game.HumanVsComputerSetupOverlay;
import com.escontrela.lastmove.ui.component.game.ThinkingIndicatorControl;
import com.escontrela.lastmove.ui.component.game.TypewriterStatusLabel;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.notation.MoveNotationNode;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.domain.service.ThreatenedSquaresService;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
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
  private static final double PLAYER_ICON_SIZE = 28.0;
  private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";

  private final UiFlowManager uiFlowManager;
  private final ComputerGameService computerGameService;
  private final AnalysisSessionService analysisSessionService;
  private final UiEventBus uiEventBus;
  private final ChessSoundService chessSoundService;
  private final CurrentUserService currentUserService;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final ComputerEngineSettingsService computerEngineSettingsService;
  private final OpeningPracticeService openingPracticeService;
  private final ThreatenedSquaresService threatenedSquaresService;
  private Timeline clockRefresh;
  private final ListChangeListener<String> themeStyleListener = change -> updatePlayerIcons();

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MoveNotationControl moveNotation;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private HumanVsComputerSetupOverlay setupOverlay;
  @FXML private MessageBox resultMessageBox;
  @FXML private Label opponentPlayerLabel;
  @FXML private Label humanPlayerLabel;
  @FXML private ImageView opponentPlayerIcon;
  @FXML private ImageView humanPlayerIcon;
  @FXML private ImageView threatHintsIcon;
  @FXML private CapturedPiecesControl opponentCapturedPieces;
  @FXML private CapturedPiecesControl humanCapturedPieces;
  @FXML private Label opponentClockLabel;
  @FXML private Label humanClockLabel;
  @FXML private TypewriterStatusLabel statusLabel;
  @FXML private ThinkingIndicatorControl opponentThinkingIndicator;
  @FXML private Button takeBackButton;
  @FXML private Button restartButton;
  @FXML private Button resignButton;
  @FXML private Button firstMoveButton;
  @FXML private Button previousMoveButton;
  @FXML private Button nextMoveButton;
  @FXML private Button lastMoveButton;

  private GameId activeGameId;
  private ComputerGameState renderedState;
  private BoardMoveInput pendingPromotionMove;
  private boolean screenVisible;
  private boolean resultShown;
  private boolean followingLivePosition = true;
  private int reviewedPlyCount;
  private boolean threatHintsEnabled;

  public HumanVsComputerScreenController(
      @Lazy UiFlowManager uiFlowManager,
      ComputerGameService computerGameService,
      AnalysisSessionService analysisSessionService,
      UiEventBus uiEventBus,
      ChessSoundService chessSoundService,
      CurrentUserService currentUserService,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      ComputerEngineSettingsService computerEngineSettingsService,
      OpeningPracticeService openingPracticeService,
      ThreatenedSquaresService threatenedSquaresService) {
    this.uiFlowManager = uiFlowManager;
    this.computerGameService = computerGameService;
    this.analysisSessionService = analysisSessionService;
    this.uiEventBus = uiEventBus;
    this.chessSoundService = chessSoundService;
    this.currentUserService = currentUserService;
    this.boardAppearancePreferencesService = boardAppearancePreferencesService;
    this.computerEngineSettingsService = computerEngineSettingsService;
    this.openingPracticeService = openingPracticeService;
    this.threatenedSquaresService = threatenedSquaresService;
  }

  @FXML
  public void initialize() {
    clockRefresh =
        new Timeline(
            new KeyFrame(javafx.util.Duration.millis(200), event -> refreshClockState()));
    clockRefresh.setCycleCount(Animation.INDEFINITE);
    root.getProperties().put("controller", this);
    root.getStyleClass().addListener(themeStyleListener);
    updatePlayerIcons();
    chessSoundService.preload();
    chessBoard.setSoundService(chessSoundService);
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    configureBoardInput();
    configurePromotionPicker();
    configureSetupOverlay();
    configureResultMessage();
    root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == javafx.scene.input.KeyCode.H && event.isShortcutDown() && event.isShiftDown()) {
        threatHintsEnabled = !threatHintsEnabled;
        refreshThreatHints();
        event.consume();
      }
    });
    bindResponsiveBoardSize();
    showEmptyWorkspace();
  }

  @Override
  public void onShow() {
    screenVisible = true;
    if (restoreGameInMemory()) {
      return;
    }
    setupOverlay.show(
        computerGameService.availableEngines(),
        currentUserService.currentUser().name(),
        computerEngineSettingsService::thinkingTime);
  }

  /** Restores a persisted game requested by a history/notification surface through the UI bus. */
  @EventListener
  public void resumeSavedGame(com.escontrela.lastmove.ui.event.ResumeComputerGameEvent event) {
    computerGameService.resumeGame(event.gameId()).whenComplete((state, failure) -> {
      if (failure == null) {
        Platform.runLater(() -> activateGame(state));
      }
    });
  }

  @Override
  public void onHide() {
    screenVisible = false;
    clockRefresh.stop();
    statusLabel.showImmediately(statusLabel.getAccessibleText());
  }

  @FXML
  public void backToMain() {
    uiFlowManager.show(UiScreenId.MAIN);
  }

  /** Stops the current game, discards its runtime and returns to the new-game setup overlay. */
  @FXML
  public void resetModel() {
    clockRefresh.stop();
    if (activeGameId != null) {
      computerGameService.closeGame(activeGameId);
      activeGameId = null;
    }
    pendingPromotionMove = null;
    resultShown = false;
    resultMessageBox.hide();
    showEmptyWorkspace();
    setupOverlay.show(
        computerGameService.availableEngines(),
        currentUserService.currentUser().name(),
        computerEngineSettingsService::thinkingTime);
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
      statusLabel.showImmediately(exception.getMessage());
    }
  }

  @FXML
  public void resign() {
    if (activeGameId != null) {
      applyState(computerGameService.resign(activeGameId));
    }
  }

  /** Shows the position before the first official move without pausing the live game. */
  @FXML
  public void reviewFirstMove() {
    if (renderedState == null || renderedState.moves().isEmpty()) {
      return;
    }
    followingLivePosition = false;
    reviewedPlyCount = 0;
    renderReviewedPosition();
  }

  /** Shows the preceding official position while clocks and engine activity continue. */
  @FXML
  public void reviewPreviousMove() {
    if (renderedState == null || reviewedPlyCount == 0) {
      return;
    }
    followingLivePosition = false;
    reviewedPlyCount--;
    renderReviewedPosition();
  }

  /** Advances one reviewed ply and resumes live following upon reaching the latest position. */
  @FXML
  public void reviewNextMove() {
    if (renderedState == null || reviewedPlyCount >= renderedState.moves().size()) {
      return;
    }
    reviewedPlyCount++;
    followingLivePosition = reviewedPlyCount == renderedState.moves().size();
    renderReviewedPosition();
  }

  /** Returns immediately to the live game position and follows later moves automatically. */
  @FXML
  public void reviewLastMove() {
    if (renderedState == null) {
      return;
    }
    followingLivePosition = true;
    reviewedPlyCount = renderedState.moves().size();
    renderReviewedPosition();
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
    setupOverlay.setOnStartGame(
        event -> {
          try {
            startGame(
                openingPracticeService.configure(
                    event.configuration(),
                    event.openingPracticeLine(),
                    event.openingPracticeThreshold()));
          } catch (IllegalArgumentException exception) {
            setupOverlay.showError(exception.getMessage());
          }
        });
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
      if (renderedState != null && !followingLivePosition) {
        statusLabel.showImmediately("Return to the live position before moving");
      }
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
      statusLabel.showImmediately(exception.getMessage());
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
                    statusLabel.showImmediately(rootCauseMessage(failure));
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
          statusLabel.showImmediately("Promotion cancelled");
        });
  }

  private void configureResultMessage() {
    resultMessageBox.setTitle("Game finished");
    resultMessageBox.setAcceptText("Analyze game");
    resultMessageBox.setCancelText("Play again");
    resultMessageBox.setOnAccept(event -> analyzeFinishedGame());
    resultMessageBox.setOnCancel(
        event -> {
          resultMessageBox.hide();
          restart();
        });
    resultMessageBox.setOnClose(event -> backToMain());
  }

  private void analyzeFinishedGame() {
    if (activeGameId == null || renderedState == null || renderedState.result().isEmpty()) {
      return;
    }
    try {
      var analysis =
          analysisSessionService.createFromGame(computerGameService.gameRecord(activeGameId));
      resultMessageBox.hide();
      uiEventBus.publish(
          new OpenAnalysisSessionEvent(
              analysis.sessionId(), "Analyzing completed game: " + analysis.title()));
      uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
    } catch (RuntimeException exception) {
      statusLabel.showImmediately(rootCauseMessage(exception));
    }
  }

  private void refreshClockState() {
    if (activeGameId == null || !screenVisible) {
      return;
    }
    try {
      applyState(computerGameService.state(activeGameId));
    } catch (RuntimeException exception) {
      clockRefresh.stop();
      statusLabel.showImmediately(exception.getMessage());
    }
  }

  private void applyState(ComputerGameState state) {
    ComputerGameState previousState = renderedState;
    renderedState = Objects.requireNonNull(state, "state must not be null");
    if (followingLivePosition) {
      reviewedPlyCount = state.moves().size();
    } else {
      reviewedPlyCount = Math.min(reviewedPlyCount, state.moves().size());
    }
    chessBoard.renderPosition(reviewedPosition());
    refreshThreatHints();
    refreshCapturedPieces();
    boolean humanIsWhite = state.humanColor() == PieceColor.WHITE;
    humanPlayerLabel.setText(
        humanIsWhite ? state.whitePlayer().getName() : state.blackPlayer().getName());
    opponentPlayerLabel.setText(
        humanIsWhite ? state.blackPlayer().getName() : state.whitePlayer().getName());
    if (previousState == null || !previousState.engine().id().equals(state.engine().id())) {
      updatePlayerIcons();
    }
    humanClockLabel.setText(
        formatClock(
            humanIsWhite ? state.clock().whiteRemaining() : state.clock().blackRemaining()));
    opponentClockLabel.setText(
        formatClock(
            humanIsWhite ? state.clock().blackRemaining() : state.clock().whiteRemaining()));
    String currentTurnText = turnText(state);
    boolean enteredHumanTurn =
        state.phase() == ComputerGamePhase.WAITING_FOR_HUMAN
            && (previousState == null
                || previousState.phase() != ComputerGamePhase.WAITING_FOR_HUMAN
                || !previousState.gameId().equals(state.gameId()));
    if (enteredHumanTurn) {
      if (state.message().isEmpty()) {
        statusLabel.play(currentTurnText);
      } else {
        statusLabel.showImmediately(state.message().orElseThrow());
      }
    } else if (state.phase() != ComputerGamePhase.WAITING_FOR_HUMAN
        || state.message().isPresent()) {
      statusLabel.showImmediately(state.message().orElse(currentTurnText));
    }
    takeBackButton.setDisable(!state.canTakeBack());
    restartButton.setDisable(false);
    resignButton.setDisable(state.result().isPresent());
    boolean engineThinking = state.phase() == ComputerGamePhase.ENGINE_THINKING;
    opponentThinkingIndicator.setThinking(engineThinking);
    refreshNotation(state.moves());
    updateReviewControls();
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
    moveNotation.setSelectedNodeId(
        reviewedPlyCount == 0 ? null : moves.get(reviewedPlyCount - 1).id());
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
    chessSoundService.play(com.escontrela.lastmove.ui.service.ChessSound.NOTIFY);
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
    humanPlayerLabel.setText(currentUserService.currentUser().name());
    opponentClockLabel.setText("--:--");
    humanClockLabel.setText("--:--");
    statusLabel.showImmediately("Choose an opponent, colour and time control");
    moveNotation.setTree(List.of());
    opponentCapturedPieces.render(List.of());
    humanCapturedPieces.render(List.of());
    takeBackButton.setDisable(true);
    restartButton.setDisable(true);
    resignButton.setDisable(true);
    opponentThinkingIndicator.setThinking(false);
    updatePlayerIcons();
    threatHintsIcon.setImage(loadImage(root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS) ? "/images/gpp_maybe_35dp_FFFFFF.png" : "/images/gpp_maybe_35dp_000000.png"));
    threatHintsIcon.setVisible(threatHintsEnabled);
    followingLivePosition = true;
    reviewedPlyCount = 0;
    updateReviewControls();
  }

  private void updatePlayerIcons() {
    String iconColor = root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS) ? "FFFFFF" : "000000";
    currentUserService.currentUserPhoto().ifPresentOrElse(
        photo -> {
          humanPlayerIcon.setImage(new Image(new ByteArrayInputStream(photo)));
          humanPlayerIcon.setClip(
              new Circle(PLAYER_ICON_SIZE / 2, PLAYER_ICON_SIZE / 2, PLAYER_ICON_SIZE / 2));
        },
        () -> {
          humanPlayerIcon.setImage(loadImage("/images/face_35dp_" + iconColor + ".png"));
          humanPlayerIcon.setClip(null);
        });
    boolean knightshadeOpponent = renderedState != null
        && ComputerEngineIds.KNIGHTSHADE.equals(renderedState.engine().id());
    opponentPlayerIcon.setImage(knightshadeOpponent
        ? loadImage(root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS)
            ? "/images/knightshade-engine-mark-dark.png"
            : "/images/knightshade-engine-mark.png")
        : loadImage("/images/robot_2_35dp_" + iconColor + ".png"));
    opponentPlayerIcon.setClip(null);
  }

  private Image loadImage(String resource) {
    return new Image(
        Objects.requireNonNull(getClass().getResource(resource), () -> "Missing image resource: " + resource)
            .toExternalForm());
  }

  private boolean canAcceptHumanInput() {
    return activeGameId != null
        && renderedState != null
        && followingLivePosition
        && renderedState.canMove();
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

  private void activateGame(ComputerGameState state) {
    setupOverlay.hide();
    activeGameId = state.gameId();
    resultShown = false;
    followingLivePosition = true;
    reviewedPlyCount = state.moves().size();
    chessBoard.setFlipped(state.humanColor() == PieceColor.BLACK);
    applyState(state);
    clockRefresh.play();
  }

  private boolean restoreGameInMemory() {
    if (activeGameId != null) {
      try {
        activateGame(computerGameService.state(activeGameId));
        return true;
      } catch (java.util.NoSuchElementException ignored) {
        activeGameId = null;
      }
    }
    List<ComputerGameState> games = computerGameService.gamesInMemory();
    if (games.isEmpty()) {
      return false;
    }
    activateGame(games.getLast());
    return true;
  }

  private void showTransitionState(String message) {
    clockRefresh.stop();
    statusLabel.showImmediately(message);
    takeBackButton.setDisable(true);
    restartButton.setDisable(true);
    resignButton.setDisable(true);
    opponentThinkingIndicator.setThinking(false);
  }

  private void showRestartFailure(Throwable failure) {
    showEmptyWorkspace();
    statusLabel.showImmediately(rootCauseMessage(failure));
    setupOverlay.show(
        computerGameService.availableEngines(),
        currentUserService.currentUser().name(),
        computerEngineSettingsService::thinkingTime);
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

  private com.escontrela.lastmove.domain.game.PositionSnapshot reviewedPosition() {
    if (reviewedPlyCount == 0) {
      return renderedState.initialPosition();
    }
    return renderedState.moves().get(reviewedPlyCount - 1).resultingPosition();
  }

  private void renderReviewedPosition() {
    chessBoard.renderPosition(reviewedPosition());
    refreshThreatHints();
    refreshCapturedPieces();
    refreshNotation(renderedState.moves());
    updateReviewControls();
    if (followingLivePosition) {
      statusLabel.showImmediately(turnText(renderedState));
    } else {
      statusLabel.showImmediately(
          "Reviewing position %d of %d · clocks continue"
              .formatted(reviewedPlyCount, renderedState.moves().size()));
    }
  }

  private void refreshThreatHints() {
    boolean show = threatHintsEnabled && renderedState != null && followingLivePosition
        && renderedState.phase() == ComputerGamePhase.WAITING_FOR_HUMAN;
    if (show) chessBoard.setThreatenedSquares(threatenedSquaresService.attackedBy(renderedState.position(), renderedState.humanColor().opposite()));
    else chessBoard.clearThreatenedSquares();
    if (threatHintsIcon != null) threatHintsIcon.setVisible(threatHintsEnabled);
  }

  private void refreshCapturedPieces() {
    if (renderedState == null) {
      opponentCapturedPieces.render(List.of());
      humanCapturedPieces.render(List.of());
      return;
    }
    List<PositionPiece> captured =
        renderedState.moves().stream()
            .limit(reviewedPlyCount)
            .flatMap(ply -> ply.capturedPiece().stream())
            .toList();
    PieceColor humanColor = renderedState.humanColor();
    humanCapturedPieces.render(
        captured.stream().filter(piece -> piece.color() != humanColor).toList());
    opponentCapturedPieces.render(
        captured.stream().filter(piece -> piece.color() == humanColor).toList());
  }

  private void updateReviewControls() {
    boolean unavailable = renderedState == null;
    int moveCount = unavailable ? 0 : renderedState.moves().size();
    firstMoveButton.setDisable(unavailable || reviewedPlyCount == 0);
    previousMoveButton.setDisable(unavailable || reviewedPlyCount == 0);
    nextMoveButton.setDisable(unavailable || reviewedPlyCount >= moveCount);
    lastMoveButton.setDisable(unavailable || followingLivePosition);
  }
}
