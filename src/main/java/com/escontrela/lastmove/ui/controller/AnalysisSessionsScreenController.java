package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.list.ManagedListCell;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.event.OpenSessionManagementEvent;
import com.escontrela.lastmove.ui.event.ReturnToAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dedicated LastMove screen for selecting, renaming and deleting retained analysis sessions.
 *
 * <p>The controller coordinates presentation actions with {@link AnalysisSessionService}. It does
 * not own chess rules or persist a globally active session: the analysis controller passes its
 * current selection in and receives the chosen identity when this screen closes.
 */
@Component
public final class AnalysisSessionsScreenController implements UiScreenController {

  private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
  private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
  private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";

  @FXML private StackPane root;
  @FXML private TextInputModal textInputModal;
  @FXML private ListView<AnalysisSessionSummary> sessionList;
  @FXML private Label emptyStateLabel;
  @FXML private Label sessionCountLabel;
  @FXML private Label statusLabel;
  @FXML private ImageView statusBrandLogo;
  @FXML private ContextualMenuPanel contextualMenuPanel;

  private final AnalysisSessionService analysisSessionService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();
  private AnalysisSessionId activeSessionId;
  private List<AnalysisSessionSummary> visibleSessions = List.of();
  private String returnStatusMessage = "Returned to analysis";

  public AnalysisSessionsScreenController(
      AnalysisSessionService analysisSessionService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.analysisSessionService = analysisSessionService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    root.getStyleClass().addListener(themeStyleListener);
    sessionList.setCellFactory(ignored -> new SessionManagementCell());
    sessionList.setPlaceholder(new Region());
    updateStatusBrandLogo();
  }

  @Override
  public void onShow() {
    refreshSessions();
  }

  /** Receives the analysis screen's current selection before this screen is displayed. */
  @EventListener
  public void prepare(OpenSessionManagementEvent event) {
    activeSessionId = event.activeSessionId();
    returnStatusMessage = "Returned to analysis";
  }

  /** Returns to the analysis workspace while preserving its screen-local selection. */
  @FXML
  public void backToAnalysis() {
    returnToAnalysis(Optional.ofNullable(activeSessionId), returnStatusMessage);
  }

  private void openSession(AnalysisSessionSummary session) {
    activeSessionId = session.sessionId();
    returnToAnalysis(
        Optional.of(activeSessionId), "Switched to " + session.title());
  }

  private void renameSession(AnalysisSessionSummary session) {
    textInputModal.setTitle("Rename session");
    textInputModal.setMessage("Choose a recognizable title for this analysis session.");
    textInputModal.setPromptText("Session title");
    textInputModal.setText(session.title());
    textInputModal.setAcceptText("Rename");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(
        event -> applySessionRename(session.sessionId(), textInputModal.getText()));
    textInputModal.setOnCancel(event -> statusLabel.setText("Rename cancelled"));
    textInputModal.show();
  }

  private void applySessionRename(AnalysisSessionId sessionId, String requestedTitle) {
    String title = requestedTitle.trim();
    if (title.isEmpty()) {
      textInputModal.setValidationMessage("Enter a session title.");
      return;
    }
    AnalysisSessionSummary renamed = analysisSessionService.renameSession(sessionId, title);
    textInputModal.hide();
    returnStatusMessage = "Renamed session to " + renamed.title();
    statusLabel.setText(returnStatusMessage);
    refreshSessions();
  }

  private void deleteSession(AnalysisSessionSummary session) {
    AnalysisSessionSummary deleted =
        analysisSessionService.deleteSession(session.sessionId());
    if (session.sessionId().equals(activeSessionId)) {
      activeSessionId = analysisSessionService.listSessions().stream()
          .findFirst()
          .map(AnalysisSessionSummary::sessionId)
          .orElse(null);
    }
    returnStatusMessage = "Deleted session: " + deleted.title();
    statusLabel.setText(returnStatusMessage);
    refreshSessions();
  }

  private void moveSession(AnalysisSessionSummary session, boolean up) {
    boolean moved =
        up
            ? analysisSessionService.moveSessionUp(session.sessionId())
            : analysisSessionService.moveSessionDown(session.sessionId());
    returnStatusMessage =
        moved
            ? "Moved session " + (up ? "up: " : "down: ") + session.title()
            : "Session is already at the " + (up ? "top" : "bottom");
    statusLabel.setText(returnStatusMessage);
    refreshSessions();
  }

  private void showSessionActions(AnalysisSessionSummary session, double sceneX, double sceneY) {
    int selectedIndex = visibleSessions.indexOf(session);
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open session", "", event -> openSession(session));
    contextualMenuPanel.addItem("Rename session…", "", event -> renameSession(session));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        "Move session up", "↑", selectedIndex <= 0, event -> moveSession(session, true));
    contextualMenuPanel.addItem(
        "Move session down",
        "↓",
        selectedIndex < 0 || selectedIndex >= visibleSessions.size() - 1,
        event -> moveSession(session, false));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete session…", "", event -> deleteSession(session));
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private void refreshSessions() {
    visibleSessions = analysisSessionService.listSessions();
    sessionList.getItems().setAll(visibleSessions);
    sessionList.refresh();
    boolean empty = visibleSessions.isEmpty();
    emptyStateLabel.setManaged(empty);
    emptyStateLabel.setVisible(empty);
    sessionCountLabel.setText(
        visibleSessions.size()
            + (visibleSessions.size() == 1 ? " session in memory" : " sessions in memory"));
  }

  private void returnToAnalysis(
      Optional<AnalysisSessionId> selectedSessionId, String statusMessage) {
    uiEventBus.publish(
        new ReturnToAnalysisSessionEvent(selectedSessionId, statusMessage));
    uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
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

  private String originLabel(AnalysisOrigin origin) {
    return switch (origin) {
      case PGN -> "PGN study";
      case INITIAL_POSITION -> "Initial position";
      case FEN -> "FEN position";
      case PLAYED_GAME -> "Played game";
    };
  }

  /** Virtualized managed-list row whose actions are exposed through its context menu. */
  private final class SessionManagementCell extends ManagedListCell<AnalysisSessionSummary> {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

    private final HBox row = new HBox(16);
    private final Label marker = new Label("✓");
    private final Label title = new Label();
    private final Label origin = new Label();

    private SessionManagementCell() {
      getStyleClass().addAll("session-management-cell", "study-library-cell");
      row.getStyleClass().addAll("session-management-row", "study-library-row");
      row.setAlignment(Pos.CENTER_LEFT);
      marker.getStyleClass().add("session-management-marker");
      title.getStyleClass().add("session-management-title");
      origin.getStyleClass().add("session-management-origin");
      VBox description = new VBox(4, title, origin);
      description.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(description, Priority.ALWAYS);
      row.getChildren().setAll(marker, description);
      row.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2
                && getItem() != null) {
              openSession(getItem());
            }
          });
      row.setOnContextMenuRequested(
          event -> {
            if (getItem() != null) {
              showSessionActions(getItem(), event.getSceneX(), event.getSceneY());
              event.consume();
            }
          });
    }

    @Override
    protected void updateItem(AnalysisSessionSummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      boolean current = item.sessionId().equals(activeSessionId);
      marker.setVisible(current);
      title.setText(item.title());
      origin.setText(originLabel(item.origin()) + (current ? " • current" : ""));
      row.pseudoClassStateChanged(CURRENT, current);
      setGraphic(row);
    }
  }
}
