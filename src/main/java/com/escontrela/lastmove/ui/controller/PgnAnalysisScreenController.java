package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.dto.AnalysisNotationNode;
import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.PgnExportService;
import com.escontrela.lastmove.application.service.EngineEvaluationService;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.comment.CommentPanel;
import com.escontrela.lastmove.ui.component.evaluation.EngineEvaluationControl;
import com.escontrela.lastmove.ui.component.evaluation.EngineSelectorModal;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.message.MultilineTextInputModal;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.notation.MoveNotationNode;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.component.session.SessionSelectorControl;
import com.escontrela.lastmove.ui.component.session.SessionSelectorEntry;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.event.OpenSessionManagementEvent;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.ReturnToAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.SelectStudyDestinationEvent;
import com.escontrela.lastmove.ui.event.SelectTacticDestinationEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ClipboardService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import com.escontrela.lastmove.ui.support.PgnFileWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
  private static final String EMPTY_COMMENT_LIGHT_ICON = "/images/mode_comment_35dp_000000.png";
  private static final String EMPTY_COMMENT_DARK_ICON = "/images/mode_comment_35dp_FFFFFF.png";
  private static final String COMMENT_LIGHT_ICON = "/images/comment_35dp_000000.png";
  private static final String COMMENT_DARK_ICON = "/images/comment_35dp_FFFFFF.png";

  private static final double BOARD_MAX_SIZE = 720.0;

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ImageView statusBrandLogo;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private TextInputModal textInputModal;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private SessionSelectorControl sessionSelector;
  @FXML private MoveNotationControl moveNotation;
  @FXML private Label statusLabel;
  @FXML private ToolbarIconButton saveSessionAsStudyButton;
  @FXML private com.escontrela.lastmove.ui.component.board.ChessBoardControl chessBoard;
  @FXML private EngineEvaluationControl engineEvaluation;
  @FXML private EngineSelectorModal engineSelectorModal;
  @FXML private CommentPanel commentPanel;
  @FXML private MultilineTextInputModal commentEditor;
  @FXML private ToolbarIconButton moveCommentButton;

  private final GameLoadService gameLoadService;
  private final AnalysisSessionService analysisSessionService;
  private final PgnExportService pgnExportService;
  private final CurrentUserService currentUserService;
  private final FileChooserFactory fileChooserFactory;
  private final PgnFileWriter pgnFileWriter;
  private final UiFlowManager uiFlowManager;
  private final UiEventBus uiEventBus;
  private final ChessSoundService chessSoundService;
  private final ClipboardService clipboardService;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final EngineEvaluationService engineEvaluationService;
  private Runnable removeEvaluationSubscription = () -> {};
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();

  /** Identity of the session currently rendered by this screen. */
  private AnalysisSessionId activeAnalysisSessionId;
  private String pendingStatusMessage;
  private BoardMoveInput pendingPromotionMove;
  private AnalysisNodeId currentNodeId;

  private List<AnalysisSessionSummary> visibleSessions = List.of();

  public PgnAnalysisScreenController(
      GameLoadService gameLoadService,
      AnalysisSessionService analysisSessionService,
      PgnExportService pgnExportService,
      CurrentUserService currentUserService,
      FileChooserFactory fileChooserFactory,
      PgnFileWriter pgnFileWriter,
      ChessSoundService chessSoundService,
      ClipboardService clipboardService,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      EngineEvaluationService engineEvaluationService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.gameLoadService = gameLoadService;
    this.analysisSessionService = analysisSessionService;
    this.pgnExportService = pgnExportService;
    this.currentUserService = currentUserService;
    this.fileChooserFactory = fileChooserFactory;
    this.pgnFileWriter = pgnFileWriter;
    this.chessSoundService = chessSoundService;
    this.clipboardService = clipboardService;
    this.boardAppearancePreferencesService = boardAppearancePreferencesService;
    this.engineEvaluationService = engineEvaluationService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {

    root.getProperties().put("controller", this);
    root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleNavigationShortcut);
    chessSoundService.preload();
    root.getStyleClass().addListener(themeStyleListener);
    updateStatusBrandLogo();
    configureContextMenu();
    chessBoard.setSoundService(chessSoundService);
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    configurePromotionPicker();
    configureEngineAnalysis();
    refreshStudyPersistenceAvailability();
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
      chessBoard.setOnPromotionRequested(
          event -> {
            pendingPromotionMove = event.getMoveInput();
            promotionPicker.showFor(event.getPromotingColor());
          });
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

  /** Opens the reusable picker and resubmits the pending board gesture once a piece is chosen. */
  private void configurePromotionPicker() {
    promotionPicker.setOnPromotionSelected(
        event -> {
          if (pendingPromotionMove == null) {
            return;
          }
          BoardMoveInput completedMove = pendingPromotionMove.withPromotion(event.pieceType());
          pendingPromotionMove = null;
          chessBoard.handleBoardMoveInput(completedMove);
        });
    promotionPicker.setOnCancel(
        event -> {
          pendingPromotionMove = null;
          statusLabel.setText("Promotion cancelled");
        });
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

  /** Exports the complete selected study, including its variations, through the native save dialog. */
  @FXML
  public void onExportSession() {
    AnalysisSessionSummary selected =
        analysisSessionService.sessionSummary(activeAnalysisSessionId);
    fileChooserFactory
        .choosePgnExportFile(root.getScene().getWindow(), selected.title())
        .ifPresentOrElse(
            file -> {
              try {
                var exportedPath =
                    pgnFileWriter.write(file, pgnExportService.export(activeAnalysisSessionId));
                statusLabel.setText("PGN exported: " + exportedPath.getFileName());
              } catch (RuntimeException exception) {
                statusLabel.setText(
                    exception.getMessage() == null
                        ? "Unable to export PGN"
                        : exception.getMessage());
              }
            },
            () -> statusLabel.setText("PGN export cancelled"));
  }

  /** Opens a destination picker to copy the active ephemeral session into a persistent chapter. */
  @FXML
  public void onSaveSessionAsStudy() {
    if (currentUserService.activePlayerState().playerId().isEmpty()) {
      statusLabel.setText("Select an active player profile before saving a study.");
      refreshStudyPersistenceAvailability();
      return;
    }
    uiEventBus.publish(new SelectStudyDestinationEvent(activeAnalysisSessionId));
    uiFlowManager.show(UiScreenId.STUDY_DESTINATION);
  }

  /** Opens the tactic-suite picker to copy the position currently shown as a new exercise. */
  @FXML
  public void onSavePositionAsTactic() {
    if (currentUserService.activePlayerState().playerId().isEmpty()) {
      statusLabel.setText("Select an active player profile before saving a tactic.");
      return;
    }
    uiEventBus.publish(new SelectTacticDestinationEvent(activeAnalysisSessionId));
    uiFlowManager.show(UiScreenId.TACTICS);
  }

  /** Receives the selection made by the dedicated session-management screen. */
  @EventListener
  public void onReturnToAnalysisSession(ReturnToAnalysisSessionEvent event) {
    activeAnalysisSessionId = event.activeSessionId().orElse(null);
    pendingStatusMessage = event.statusMessage();
  }

  /** Receives a newly created study before this screen is opened from another workflow. */
  @EventListener
  public void onOpenAnalysisSession(OpenAnalysisSessionEvent event) {
    activeAnalysisSessionId = event.sessionId();
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

  /** Handles the analysis-navigation arrows unless an editable or modal UI element owns input. */
  private void handleNavigationShortcut(KeyEvent event) {
    if (event.isConsumed()
        || textInputModal.isVisible()
        || promotionPicker.isVisible()
        || contextualMenuPanel.isVisible()
        || root.getScene() == null
        || root.getScene().getFocusOwner() instanceof TextInputControl) {
      return;
    }

    switch (event.getCode()) {
      case UP -> onFirstMove();
      case DOWN -> onLastMove();
      case LEFT -> onPreviousMove();
      case RIGHT -> onNextMove();
      default -> {
        return;
      }
    }
    event.consume();
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

  /** Rotates only the reusable board presentation, leaving the active session untouched. */
  @FXML
  public void onRotateBoard() {
    boolean flipped = chessBoard.toggleOrientation();
    statusLabel.setText(flipped ? "Board rotated: Black at bottom" : "Board rotated: White at bottom");
  }

  /** Opens the annotation associated with the SAN move currently selected in the session. */
  @FXML
  public void onMoveComment() {
    if (currentNodeId == null) {
      return;
    }
    AnalysisNodeId annotatedNodeId = currentNodeId;
    String title = "Move comment · " + selectedSan().orElse("move");
    commentPanel.setTitle(title);
    commentPanel.setContent(
        analysisSessionService
            .moveComment(activeAnalysisSessionId, annotatedNodeId)
            .orElse(""));
    commentPanel.setOnEdit(
        event ->
            commentEditor.show(
                title,
                commentPanel.getContent(),
                saveEvent -> {
                  analysisSessionService.saveMoveComment(
                      activeAnalysisSessionId, annotatedNodeId, commentEditor.getText());
                  commentPanel.setContent(commentEditor.getText().strip());
                  commentEditor.hide();
                  refreshMoveCommentIcon();
                  statusLabel.setText(
                      commentPanel.getContent().isBlank()
                          ? "Move comment removed"
                          : "Move comment saved");
                }));
    commentPanel.show();
  }

  private Optional<String> selectedSan() {
    return currentNodeId == null
        ? Optional.empty()
        : findSan(moveNotation.getTree(), currentNodeId.value());
  }

  private Optional<String> findSan(List<MoveNotationNode> nodes, java.util.UUID nodeId) {
    for (MoveNotationNode node : nodes) {
      if (node.entry().nodeId().equals(nodeId)) {
        return Optional.of(node.entry().san());
      }
      Optional<String> nested = findSan(node.continuations(), nodeId);
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
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
    contextualMenuPanel.addItem("Export PGN…", "", event -> onExportSession());
    contextualMenuPanel.addItem("Copy position as FEN", "", event -> onCopyFen());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("First move", "↑", event -> onFirstMove());
    contextualMenuPanel.addItem("Previous move", "←", event -> onPreviousMove());
    contextualMenuPanel.addItem("Next move", "→", event -> onNextMove());
    contextualMenuPanel.addItem("Last move", "↓", event -> onLastMove());
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
    int selectedIndex = visibleSessions.indexOf(selected);
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem(
        "Open session", "", event -> activateSession(selectedIndex));
    contextualMenuPanel.addItem(
        "Rename session…",
        "",
        event -> showRenameSessionModal(selected.sessionId(), selected.title()));
    contextualMenuPanel.addItem(
        "Delete session", "", event -> deleteSession(selected.sessionId()));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        "Move session up", "↑", selectedIndex <= 0, event -> moveSession(selected, true));
    contextualMenuPanel.addItem(
        "Move session down",
        "↓",
        selectedIndex < 0 || selectedIndex >= visibleSessions.size() - 1,
        event -> moveSession(selected, false));
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
          var selectedNodeId =
              new com.escontrela.lastmove.domain.analysis.AnalysisNodeId(
                  event.getEntry().nodeId());
          chessBoard.renderPosition(
              analysisSessionService.select(activeAnalysisSessionId, selectedNodeId));
          refreshMoveList();
          statusLabel.setText("Selected " + event.getEntry().san());
        });
  }

  private void refreshWorkspace() {
    chessBoard.renderPosition(analysisSessionService.currentPosition(activeAnalysisSessionId));
    refreshSessionList();
    refreshMoveList();
    statusLabel.setText(
        "Ready: " + analysisSessionService.sessionSummary(activeAnalysisSessionId).title());
    refreshStudyPersistenceAvailability();
  }

  private void refreshStudyPersistenceAvailability() {
    boolean available = currentUserService.activePlayerState().playerId().isPresent();
    saveSessionAsStudyButton.setDisable(!available);
    saveSessionAsStudyButton.setTooltipText(
        available
            ? "Save session as a chapter"
            : "Select an active player profile to save this session as a chapter");
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
    AnalysisNotationTree notationTree =
        analysisSessionService.notationTree(activeAnalysisSessionId);
    moveNotation.setTree(notationTree.roots().stream().map(this::toNotationNode).toList());
    moveNotation.setSelectedNodeId(
        notationTree.currentNodeId().map(nodeId -> nodeId.value()).orElse(null));
    currentNodeId = notationTree.currentNodeId().orElse(null);
    moveCommentButton.setDisable(currentNodeId == null);
    refreshMoveCommentIcon();
    if (commentPanel.isVisible() && commentPanel.getTitle().startsWith("Move comment")) {
      onMoveComment();
    }
  }

  private MoveNotationNode toNotationNode(AnalysisNotationNode node) {
    var ply = node.ply();
    return new MoveNotationNode(
        new MoveNotationEntry(
            node.nodeId().value(),
            ply.moveNumber(),
            ply.movingColor() == com.escontrela.lastmove.domain.common.PieceColor.WHITE,
            ply.move().san().getValue(),
            node.activeLine()),
        node.continuations().stream().map(this::toNotationNode).toList());
  }

  private void refreshMoveCommentIcon() {
    boolean hasComment =
        currentNodeId != null
            && analysisSessionService
                .moveComment(activeAnalysisSessionId, currentNodeId)
                .isPresent();
    moveCommentButton.setLightIconResource(
        hasComment ? COMMENT_LIGHT_ICON : EMPTY_COMMENT_LIGHT_ICON);
    moveCommentButton.setDarkIconResource(
        hasComment ? COMMENT_DARK_ICON : EMPTY_COMMENT_DARK_ICON);
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

  @Override
  public void onHide() {
    removeEvaluationSubscription.run();
    engineEvaluationService.cancel();
  }

  private void configureEngineAnalysis() {
    removeEvaluationSubscription = engineEvaluationService.subscribe(
        state -> Platform.runLater(() -> engineEvaluation.render(state)));
    engineEvaluation.setOnChangeEngine(
        event -> engineSelectorModal.show(
            engineEvaluationService.availableEngines(), engineEvaluationService.state().engine().id()));
    engineSelectorModal.setOnEngineSelected(
        event -> engineEvaluationService.selectEngine(event.engineId()));
    chessBoard.positionProperty().addListener(
        (observable, oldPosition, newPosition) -> {
          if (newPosition != null) {
            engineEvaluationService.analyze(newPosition);
          }
        });
  }
}
