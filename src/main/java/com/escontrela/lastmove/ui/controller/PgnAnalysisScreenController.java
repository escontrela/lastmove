package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.GameSessionSummary;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.GameSessionService;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * FXML controller for the main application screen.
 *
 * <p>Delegates all chess logic to application services. This controller is responsible only for
 * routing UI events and updating the view model.
 */
@Component
public class PgnAnalysisScreenController implements UiScreenController {

  private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
  private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
  private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";

  private static final double BOARD_MAX_SIZE = 720.0;

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ImageView statusBrandLogo;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private ListView<String> sessionListView;
  @FXML private ListView<String> moveListView;
  @FXML private Label statusLabel;
  @FXML private com.escontrela.lastmove.ui.component.board.ChessBoardControl chessBoard;

  private final GameLoadService gameLoadService;
  private final GameSessionService gameSessionService;
  private final FileChooserFactory fileChooserFactory;
  private final UiFlowManager uiFlowManager;
  private final ChessSoundService chessSoundService;
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();

  /** Identity of the session currently rendered by this screen. */
  private SessionId boardSessionId;

  private List<GameSessionSummary> visibleSessions = List.of();

  public PgnAnalysisScreenController(
      GameLoadService gameLoadService,
      GameSessionService gameSessionService,
      FileChooserFactory fileChooserFactory,
      ChessSoundService chessSoundService,
      @Lazy UiFlowManager uiFlowManager) {
    this.gameLoadService = gameLoadService;
    this.gameSessionService = gameSessionService;
    this.fileChooserFactory = fileChooserFactory;
    this.chessSoundService = chessSoundService;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {

    root.getProperties().put("controller", this);
    chessSoundService.preload();
    root.getStyleClass().addListener(themeStyleListener);
    updateStatusBrandLogo();
    configureContextMenu();
    chessBoard.setSoundService(chessSoundService);
    boardSessionId = gameSessionService.createInitialSession().sessionId();
    chessBoard.renderPosition(gameSessionService.currentPosition(boardSessionId));
    configureSessionPicker();
    refreshWorkspace();

    // Suscribirse a los eventos de movimiento desde el tablero
    if (chessBoard != null) {
      chessBoard.setOnMoveRequested(
          event -> {
            BoardMoveInput moveInput = event.getMoveInput();
            MoveExecutionResult moveResult =
                gameSessionService.attemptMove(
                    boardSessionId,
                    new MoveCommand(
                        moveInput.fromSquare(), moveInput.toSquare(), moveInput.promotionPiece()));

            if (moveResult.accepted()) {
              chessBoard.renderPosition(moveResult.newSnapshot());
              refreshMoveList();
            } else {
              // A dedicated status/message component can render this later without changing flow.
              moveResult.rejectionReason().ifPresent(reason -> root.setAccessibleHelp(reason));
            }
          });
    }

    bindResponsiveBoardSize();
  }

  /**
   * Hace que el tablero sea responsive: mantiene proporción 1:1, se ajusta al espacio disponible
   * del host y nunca supera {@link #BOARD_MAX_SIZE} (el tamaño ya validado en pantalla maximizada).
   *
   * <p>Calculamos nosotros mismos el lado del tablero cada vez que cambia el tamaño del host, en
   * lugar de encadenar bindings de JavaFX: así evitamos que casillas y piezas queden mal
   * redimensionadas cuando la ventana no está maximizada.
   */
  private void bindResponsiveBoardSize() {

    if (boardHost == null || chessBoard == null) {
      return;
    }

    ChangeListener<Number> recompute = (observable, oldValue, newValue) -> updateBoardSize();
    boardHost.widthProperty().addListener(recompute);
    boardHost.heightProperty().addListener(recompute);
    updateBoardSize();
  }

  private void updateBoardSize() {

    double available = Math.min(boardHost.getWidth(), boardHost.getHeight());
    if (available <= 0) {
      return;
    }

    double side = Math.min(available, BOARD_MAX_SIZE);
    chessBoard.setPrefWidth(side);
    chessBoard.setPrefHeight(side);
  }

  @FXML
  public void onOpenPgn() {
    fileChooserFactory
        .choosePgnFile(root.getScene().getWindow())
        .ifPresent(
            file -> {
              try {
                boardSessionId =
                    gameSessionService
                        .createPgnSession(
                            gameLoadService.importPgn(PgnImportRequest.fromFile(file.toPath())))
                        .sessionId();
                refreshWorkspace();
              } catch (IllegalArgumentException exception) {
                statusLabel.setText(exception.getMessage());
              }
            });
  }

  /** Starts and activates a fresh study at the normal initial position. */
  @FXML
  public void onReset() {
    boardSessionId = gameSessionService.createInitialSession().sessionId();
    refreshWorkspace();
  }

  /** Prompts for a FEN and starts a new session from the accepted position. */
  @FXML
  public void onFen() {
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Start from FEN");
    dialog.setHeaderText("Create a study from a FEN position");
    dialog.setContentText("FEN:");
    dialog
        .showAndWait()
        .filter(value -> !value.isBlank())
        .ifPresent(
            value -> {
              try {
                boardSessionId =
                    gameSessionService
                        .createFenSession(
                            com.escontrela.lastmove.domain.notation.Fen.of(value.trim()))
                        .sessionId();
                refreshWorkspace();
              } catch (IllegalArgumentException exception) {
                statusLabel.setText(exception.getMessage());
              }
            });
  }

  /** Opens a modal picker for every session retained in the in-memory catalog. */
  @FXML
  public void onShowSessions() {
    List<GameSessionSummary> sessions = gameSessionService.listSessions();
    Dialog<SessionId> dialog = new Dialog<>();
    dialog.setTitle("Open sessions");
    dialog.setHeaderText("Return to an in-memory study");
    ListView<String> choices = new ListView<>();
    choices.getItems().setAll(sessions.stream().map(GameSessionSummary::title).toList());
    choices.setPrefHeight(240);
    for (int index = 0; index < sessions.size(); index++) {
      if (sessions.get(index).sessionId().equals(boardSessionId)) {
        choices.getSelectionModel().select(index);
        break;
      }
    }
    dialog.getDialogPane().setContent(choices);
    ButtonType select = new ButtonType("Open", ButtonType.OK.getButtonData());
    dialog.getDialogPane().getButtonTypes().addAll(select, ButtonType.CANCEL);
    dialog.setResultConverter(
        button ->
            button == select && choices.getSelectionModel().getSelectedIndex() >= 0
                ? sessions.get(choices.getSelectionModel().getSelectedIndex()).sessionId()
                : null);
    dialog
        .showAndWait()
        .ifPresent(
            sessionId -> {
              boardSessionId = sessionId;
              refreshWorkspace();
            });
  }

  @FXML
  public void onNextMove() {
    chessBoard.renderPosition(gameSessionService.next(boardSessionId));
    refreshMoveList();
  }

  @FXML
  public void onPreviousMove() {
    chessBoard.renderPosition(gameSessionService.previous(boardSessionId));
    refreshMoveList();
  }

  @FXML
  public void backToMain() {
    uiFlowManager.show(UiScreenId.MAIN);
  }

  @FXML
  public void openSetup() {
    uiFlowManager.show(UiScreenId.SETUP);
  }

  @FXML
  public void showContextMenu(ContextMenuEvent event) {
    contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
    event.consume();
  }

  private void configureContextMenu() {

    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open PGN…", "⌘ O", event -> onOpenPgn());
    contextualMenuPanel.addItem("Initial position", "", event -> onReset());
    contextualMenuPanel.addItem("Start from FEN…", "", event -> onFen());
    contextualMenuPanel.addItem("Open sessions…", "", event -> onShowSessions());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Previous move", "←", event -> onPreviousMove());
    contextualMenuPanel.addItem("Next move", "→", event -> onNextMove());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Back to chess tools", "", event -> backToMain());
    contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
  }

  private void configureSessionPicker() {
    sessionListView
        .getSelectionModel()
        .selectedIndexProperty()
        .addListener(
            (observable, previous, selected) -> {
              if (selected == null
                  || selected.intValue() < 0
                  || selected.intValue() >= visibleSessions.size()) {
                return;
              }
              boardSessionId = visibleSessions.get(selected.intValue()).sessionId();
              chessBoard.renderPosition(gameSessionService.currentPosition(boardSessionId));
              refreshMoveList();
              statusLabel.setText(
                  "Switched to " + visibleSessions.get(selected.intValue()).title());
            });
  }

  private void refreshWorkspace() {
    chessBoard.renderPosition(gameSessionService.currentPosition(boardSessionId));
    refreshSessionList();
    refreshMoveList();
    statusLabel.setText("Ready: " + gameSessionService.sessionSummary(boardSessionId).title());
  }

  private void refreshSessionList() {
    visibleSessions = gameSessionService.listSessions();
    sessionListView
        .getItems()
        .setAll(
            visibleSessions.stream()
                .map(
                    summary ->
                        (summary.sessionId().equals(boardSessionId) ? "● " : "") + summary.title())
                .toList());
    for (int index = 0; index < visibleSessions.size(); index++) {
      if (visibleSessions.get(index).sessionId().equals(boardSessionId)) {
        sessionListView.getSelectionModel().select(index);
        break;
      }
    }
  }

  private void refreshMoveList() {
    int currentPlyIndex = gameSessionService.moveHistory(boardSessionId).size() - 1;
    moveListView
        .getItems()
        .setAll(
            gameSessionService.notationLine(boardSessionId).stream()
                .map(
                    ply ->
                        ply.moveNumber()
                            + (ply.movingColor().name().equals("WHITE") ? ". " : "... ")
                            + ply.move().san().getValue())
                .toList());
    if (currentPlyIndex >= 0) {
      moveListView.getSelectionModel().select(currentPlyIndex);
      moveListView.scrollTo(currentPlyIndex);
    } else {
      moveListView.getSelectionModel().clearSelection();
    }
  }

  private void updateStatusBrandLogo() {
    String resource =
        root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS)
            ? DARK_LOGO_RESOURCE
            : LIGHT_LOGO_RESOURCE;
    statusBrandLogo.setImage(
        new Image(
            Objects.requireNonNull(
                    getClass().getResource(resource),
                    () -> "Missing status logo resource: " + resource)
                .toExternalForm()));
  }
}
