package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

  @FXML private BorderPane root;
  @FXML private ListView<AnalysisSessionSummary> sessionList;
  @FXML private Label emptyStateLabel;
  @FXML private Label sessionCountLabel;
  @FXML private Label statusLabel;
  @FXML private ImageView statusBrandLogo;

  private final AnalysisSessionService analysisSessionService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();
  private AnalysisSessionId activeSessionId;
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
    TextInputDialog dialog = new TextInputDialog(session.title());
    dialog.setTitle("Rename session");
    dialog.setHeaderText("Choose a recognizable study title");
    dialog.setContentText("Title:");
    dialog
        .showAndWait()
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .ifPresent(
            value -> {
              AnalysisSessionSummary renamed =
                  analysisSessionService.renameSession(session.sessionId(), value);
              returnStatusMessage = "Renamed session to " + renamed.title();
              statusLabel.setText(returnStatusMessage);
              refreshSessions();
            });
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

  private void refreshSessions() {
    List<AnalysisSessionSummary> sessions = analysisSessionService.listSessions();
    sessionList.getItems().setAll(sessions);
    sessionList.refresh();
    boolean empty = sessions.isEmpty();
    emptyStateLabel.setManaged(empty);
    emptyStateLabel.setVisible(empty);
    sessionCountLabel.setText(
        sessions.size() + (sessions.size() == 1 ? " session in memory" : " sessions in memory"));
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

  /** Virtualized row with explicit open, rename and delete actions. */
  private final class SessionManagementCell extends ListCell<AnalysisSessionSummary> {

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

    private final HBox row = new HBox(16);
    private final Label marker = new Label("✓");
    private final Label title = new Label();
    private final Label origin = new Label();
    private final Button open = new Button("Open");
    private final Button rename = new Button("Rename");
    private final Button delete = new Button("Delete");

    private SessionManagementCell() {
      getStyleClass().add("session-management-cell");
      row.getStyleClass().add("session-management-row");
      row.setAlignment(Pos.CENTER_LEFT);
      marker.getStyleClass().add("session-management-marker");
      title.getStyleClass().add("session-management-title");
      origin.getStyleClass().add("session-management-origin");
      VBox description = new VBox(4, title, origin);
      description.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(description, Priority.ALWAYS);
      open.getStyleClass().addAll("session-action-button", "session-open-button");
      rename.getStyleClass().addAll("session-action-button", "session-rename-button");
      delete.getStyleClass().addAll("session-action-button", "session-delete-button");
      row.getChildren().setAll(marker, description, open, rename, delete);
      open.setOnAction(event -> openSession(getItem()));
      rename.setOnAction(event -> renameSession(getItem()));
      delete.setOnAction(event -> deleteSession(getItem()));
      row.setOnMouseClicked(
          event -> {
            if (event.getClickCount() == 2 && getItem() != null) {
              openSession(getItem());
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
