package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.dto.AnalysisNodeSummary;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.session.SessionSelectorControl;
import com.escontrela.lastmove.ui.component.session.SessionSelectorEntry;
import com.escontrela.lastmove.ui.event.OpenSessionManagementEvent;
import com.escontrela.lastmove.ui.event.ReturnToAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.service.ClipboardService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.util.List;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
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
  @FXML private TextInputModal textInputModal;
  @FXML private SessionSelectorControl sessionSelector;
  @FXML private MoveNotationControl moveNotation;
  @FXML private Label statusLabel;
  @FXML private com.escontrela.lastmove.ui.component.board.ChessBoardControl chessBoard;

  private final GameLoadService gameLoadService;
  private final AnalysisSessionService analysisSessionService;
  private final FileChooserFactory fileChooserFactory;
  private final UiFlowManager uiFlowManager;
  private final UiEventBus uiEventBus;
  private final ChessSoundService chessSoundService;
  private final ClipboardService clipboardService;
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();

  /** Identity of the session currently rendered by this screen. */
  private AnalysisSessionId activeAnalysisSessionId;
  private String pendingStatusMessage;

  private List<AnalysisSessionSummary> visibleSessions = List.of();
  private List<AnalysisNodeSummary> visibleNotationNodes = List.of();

  public PgnAnalysisScreenController(
      GameLoadService gameLoadService,
      AnalysisSessionService analysisSessionService,
      FileChooserFactory fileChooserFactory,
      ChessSoundService chessSoundService,
      ClipboardService clipboardService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.gameLoadService = gameLoadService;
    this.analysisSessionService = analysisSessionService;
    this.fileChooserFactory = fileChooserFactory;
    this.chessSoundService = chessSoundService;
    this.clipboardService = clipboardService;
    this.uiEventBus = uiEventBus;
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
    if (activeAnalysisSessionId == null) {
      activeAnalysisSessionId = analysisSessionService.createInitialSession().sessionId();
    }
    chessBoard.renderPosition(analysisSessionService.currentPosition(activeAnalysisSessionId));
    configureSessionPicker();
    configureMoveNotation();
    refreshWorkspace();
    if (pendingStatusMessage != null && !pendingStatusMessage.isBlank()) {
      statusLabel.setText(pendingStatusMessage);
      pendingStatusMessage = null;
    }

    // Suscribirse a los eventos de movimiento desde el tablero
    if (chessBoard != null) {
      chessBoard.setOnMoveRequested(
          event -> {
            BoardMoveInput moveInput = event.getMoveInput();
            MoveExecutionResult moveResult =
                analysisSessionService.attemptMove(
                    activeAnalysisSessionId,
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
                activeAnalysisSessionId =
                    analysisSessionService
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
    activeAnalysisSessionId = analysisSessionService.createInitialSession().sessionId();
    refreshWorkspace();
  }

  /** Prompts for a FEN and starts a new session from the accepted position. */
  @FXML
  public void onFen() {
    configureTextInput(
        "Start from FEN",
        "Create a study from a Forsyth-Edwards Notation position.",
        "Paste a FEN position",
        "",
        "Create study",
        this::createFenSession);
  }

  /** Opens the LastMove session-management screen without creating a native modal window. */
  @FXML
  public void onShowSessions() {
    uiEventBus.publish(new OpenSessionManagementEvent(activeAnalysisSessionId));
    uiFlowManager.show(UiScreenId.ANALYSIS_SESSIONS);
  }

  /** Receives the selection made by the dedicated session-management screen. */
  @EventListener
  public void onReturnToAnalysisSession(ReturnToAnalysisSessionEvent event) {
    activeAnalysisSessionId = event.activeSessionId().orElse(null);
    pendingStatusMessage = event.statusMessage();
  }

  @FXML
  public void onNextMove() {
    chessBoard.renderPosition(analysisSessionService.next(activeAnalysisSessionId));
    refreshMoveList();
  }

  @FXML
  public void onPreviousMove() {
    chessBoard.renderPosition(analysisSessionService.previous(activeAnalysisSessionId));
    refreshMoveList();
  }

  /** Returns to the initial position preceding the first move of the visible line. */
  @FXML
  public void onFirstMove() {
    chessBoard.renderPosition(analysisSessionService.first(activeAnalysisSessionId));
    refreshMoveList();
    statusLabel.setText("Moved to the initial position");
  }

  /** Advances to the final move of the current preferred continuation. */
  @FXML
  public void onLastMove() {
    chessBoard.renderPosition(analysisSessionService.last(activeAnalysisSessionId));
    refreshMoveList();
    statusLabel.setText("Moved to the last move");
  }

  /** Copies the complete FEN represented by the board's active analysis position. */
  @FXML
  public void onCopyFen() {
    String fen = analysisSessionService.currentFen(activeAnalysisSessionId);
    if (clipboardService.copyText(fen)) {
      statusLabel.setText("FEN copied to clipboard");
    } else {
      statusLabel.setText("Unable to copy FEN to clipboard");
    }
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
    configureContextMenu();
    contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
    event.consume();
  }

  private void configureContextMenu() {

    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open PGN…", "⌘ O", event -> onOpenPgn());
    contextualMenuPanel.addItem("Initial position", "", event -> onReset());
    contextualMenuPanel.addItem("Start from FEN…", "", event -> onFen());
    contextualMenuPanel.addItem("Open sessions…", "", event -> onShowSessions());
    contextualMenuPanel.addItem("Copy position as FEN", "", event -> onCopyFen());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("First move", "⇤", event -> onFirstMove());
    contextualMenuPanel.addItem("Previous move", "←", event -> onPreviousMove());
    contextualMenuPanel.addItem("Next move", "→", event -> onNextMove());
    contextualMenuPanel.addItem("Last move", "⇥", event -> onLastMove());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Back to chess tools", "", event -> backToMain());
    contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
  }

  private void configureSessionPicker() {
    sessionSelector.setOnSessionSelected(
        event -> activateSession(event.getEntry().sessionIndex()));
    sessionSelector.setOnContextRequested(
        event -> {
          int sessionIndex = event.getEntry().sessionIndex();
          if (sessionIndex < 0 || sessionIndex >= visibleSessions.size()) {
            return;
          }
          AnalysisSessionSummary selected = visibleSessions.get(sessionIndex);
          configureSessionContextMenu(selected);
          contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
        });
  }

  private void activateSession(int sessionIndex) {
    if (sessionIndex < 0 || sessionIndex >= visibleSessions.size()) {
      return;
    }
    AnalysisSessionSummary selected = visibleSessions.get(sessionIndex);
    activeAnalysisSessionId = selected.sessionId();
    chessBoard.renderPosition(analysisSessionService.currentPosition(activeAnalysisSessionId));
    refreshMoveList();
    refreshSessionList();
    statusLabel.setText("Switched to " + selected.title());
  }

  private void configureSessionContextMenu(AnalysisSessionSummary selected) {
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem(
        "Rename session…",
        "",
        event -> showRenameSessionModal(selected.sessionId(), selected.title()));
    contextualMenuPanel.addItem(
        "Delete session", "", event -> deleteSession(selected.sessionId()));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        "Move session up", "↑", event -> moveSession(selected, true));
    contextualMenuPanel.addItem(
        "Move session down", "↓", event -> moveSession(selected, false));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Open sessions…", "", event -> onShowSessions());
  }

  private void moveSession(AnalysisSessionSummary session, boolean up) {
    boolean moved =
        up
            ? analysisSessionService.moveSessionUp(session.sessionId())
            : analysisSessionService.moveSessionDown(session.sessionId());
    refreshSessionList();
    statusLabel.setText(
        moved
            ? "Moved session " + (up ? "up: " : "down: ") + session.title()
            : "Session is already at the " + (up ? "top" : "bottom"));
  }

  private void deleteSession(AnalysisSessionId sessionId) {
    AnalysisSessionSummary deleted = analysisSessionService.deleteSession(sessionId);
    if (sessionId.equals(activeAnalysisSessionId)) {
      List<AnalysisSessionSummary> remaining = analysisSessionService.listSessions();
      activeAnalysisSessionId =
          remaining.isEmpty()
              ? analysisSessionService.createInitialSession().sessionId()
              : remaining.getFirst().sessionId();
      chessBoard.renderPosition(analysisSessionService.currentPosition(activeAnalysisSessionId));
      refreshMoveList();
    }
    refreshSessionList();
    statusLabel.setText("Deleted session: " + deleted.title());
  }

  private void showRenameSessionModal(AnalysisSessionId sessionId, String currentTitle) {
    configureTextInput(
        "Rename session",
        "Choose a recognizable title for this analysis session.",
        "Session title",
        currentTitle,
        "Rename",
        value -> applySessionRename(sessionId, value));
  }

  private void createFenSession(String rawFen) {
    String fen = rawFen.trim();
    if (fen.isEmpty()) {
      textInputModal.setValidationMessage("Enter a FEN position.");
      return;
    }
    try {
      activeAnalysisSessionId =
          analysisSessionService
              .createFenSession(com.escontrela.lastmove.domain.notation.Fen.of(fen))
              .sessionId();
      textInputModal.hide();
      refreshWorkspace();
    } catch (IllegalArgumentException exception) {
      String message = exception.getMessage();
      textInputModal.setValidationMessage(
          message == null || message.isBlank() ? "The FEN position is invalid." : message);
    }
  }

  private void applySessionRename(AnalysisSessionId sessionId, String requestedTitle) {
    String title = requestedTitle.trim();
    if (title.isEmpty()) {
      textInputModal.setValidationMessage("Enter a session title.");
      return;
    }
    AnalysisSessionSummary renamed = analysisSessionService.renameSession(sessionId, title);
    textInputModal.hide();
    refreshSessionList();
    statusLabel.setText("Renamed session to " + renamed.title());
  }

  private void configureTextInput(
      String title,
      String message,
      String prompt,
      String initialValue,
      String acceptText,
      java.util.function.Consumer<String> onAccepted) {
    textInputModal.setTitle(title);
    textInputModal.setMessage(message);
    textInputModal.setPromptText(prompt);
    textInputModal.setText(initialValue);
    textInputModal.setAcceptText(acceptText);
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(event -> onAccepted.accept(textInputModal.getText()));
    textInputModal.setOnCancel(event -> statusLabel.setText(title + " cancelled"));
    textInputModal.show();
  }

  private void configureMoveNotation() {
    moveNotation.setOnPlySelected(
        event -> {
          int plyIndex = event.getEntry().plyIndex();
          if (plyIndex < 0 || plyIndex >= visibleNotationNodes.size()) {
            return;
          }
          AnalysisNodeSummary selected = visibleNotationNodes.get(plyIndex);
          chessBoard.renderPosition(
              analysisSessionService.select(activeAnalysisSessionId, selected.nodeId()));
          refreshMoveList();
          statusLabel.setText("Selected " + selected.ply().move().san().getValue());
        });
  }

  private void refreshWorkspace() {
    chessBoard.renderPosition(analysisSessionService.currentPosition(activeAnalysisSessionId));
    refreshSessionList();
    refreshMoveList();
    statusLabel.setText(
        "Ready: " + analysisSessionService.sessionSummary(activeAnalysisSessionId).title());
  }

  private void refreshSessionList() {
    visibleSessions = analysisSessionService.listSessions();
    sessionSelector.setEntries(
        java.util.stream.IntStream.range(0, visibleSessions.size())
            .mapToObj(
                sessionIndex ->
                    new SessionSelectorEntry(
                        sessionIndex, visibleSessions.get(sessionIndex).title()))
            .toList());
    int selectedIndex = -1;
    for (int index = 0; index < visibleSessions.size(); index++) {
      if (visibleSessions.get(index).sessionId().equals(activeAnalysisSessionId)) {
        selectedIndex = index;
        break;
      }
    }
    sessionSelector.setSelectedSessionIndex(selectedIndex);
  }

  private void refreshMoveList() {
    int currentPlyIndex =
        analysisSessionService.moveHistory(activeAnalysisSessionId).size() - 1;
    visibleNotationNodes = analysisSessionService.notationNodes(activeAnalysisSessionId);
    moveNotation.setEntries(
        java.util.stream.IntStream.range(0, visibleNotationNodes.size())
            .mapToObj(
                plyIndex -> {
                  var ply = visibleNotationNodes.get(plyIndex).ply();
                  return new MoveNotationEntry(
                      plyIndex,
                      ply.moveNumber(),
                      ply.movingColor() == PieceColor.WHITE,
                      ply.move().san().getValue());
                })
            .toList());
    moveNotation.setSelectedPlyIndex(currentPlyIndex);
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
