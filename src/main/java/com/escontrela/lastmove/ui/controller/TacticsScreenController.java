package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.TacticService;
import com.escontrela.lastmove.application.tactics.CreateTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.CopyAnalysisSessionTacticCommand;
import com.escontrela.lastmove.application.tactics.TacticSuiteSummary;
import com.escontrela.lastmove.application.tactics.TacticExerciseSummary;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.event.OpenTacticsWorkspaceEvent;
import com.escontrela.lastmove.ui.event.SelectTacticDestinationEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Library of tactical suites owned by the selected player. */
@Component
public final class TacticsScreenController implements UiScreenController {

  @FXML private StackPane root;
  @FXML private ListView<TacticSuiteSummary> suiteList;
  @FXML private Label suiteCountLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label statusLabel;
  @FXML private TextInputModal textInputModal;

  private final TacticService tacticService;
  private final CurrentUserService currentUserService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private Optional<PlayerId> ownerId = Optional.empty();
  private com.escontrela.lastmove.domain.analysis.AnalysisSessionId pendingAnalysisSessionId;

  public TacticsScreenController(
      TacticService tacticService,
      CurrentUserService currentUserService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.tacticService = tacticService;
    this.currentUserService = currentUserService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    suiteList.setCellFactory(ignored -> new SuiteCell());
  }

  @Override
  public void onShow() {
    refresh();
  }

  @EventListener
  public void onSelectTacticDestination(SelectTacticDestinationEvent event) {
    pendingAnalysisSessionId = event.sessionId();
  }

  @FXML
  public void onCreateSuite() {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      statusLabel.setText("Select a player profile before creating a suite.");
      return;
    }
    textInputModal.setTitle("Create tactic suite");
    textInputModal.setMessage("Group related tactical exercises under one concise title.");
    textInputModal.setPromptText("Suite title");
    textInputModal.setText("");
    textInputModal.setAcceptText("Create suite");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(event -> createSuite(owner, textInputModal.getText()));
    textInputModal.show();
  }

  @FXML
  public void backToMain() {
    uiFlowManager.show(UiScreenId.MAIN);
  }

  private void createSuite(PlayerId owner, String requestedTitle) {
    String title = requestedTitle.trim();
    if (title.isEmpty()) {
      textInputModal.setValidationMessage("Enter a suite title.");
      return;
    }
    TacticSuiteSummary suite =
        tacticService.createSuite(new CreateTacticSuiteCommand(owner, title, Optional.empty()));
    textInputModal.hide();
    openSuite(suite);
  }

  private void refresh() {
    ownerId = currentUserService.activePlayerState().playerId();
    if (ownerId.isEmpty()) {
      suiteList.getItems().clear();
      suiteCountLabel.setText("0 suites");
      emptyStateLabel.setText("Choose an active player profile to create tactical suites.");
      emptyStateLabel.setVisible(true);
      emptyStateLabel.setManaged(true);
      return;
    }
    List<TacticSuiteSummary> suites = tacticService.listSuites(ownerId.orElseThrow());
    suiteList.getItems().setAll(suites);
    suiteCountLabel.setText(suites.size() + (suites.size() == 1 ? " suite" : " suites"));
    emptyStateLabel.setText("Create a suite, then add a position and author its solution line.");
    emptyStateLabel.setVisible(suites.isEmpty());
    emptyStateLabel.setManaged(suites.isEmpty());
    statusLabel.setText(suites.isEmpty() ? "Ready to create your first tactic suite" : "Open a suite to train");
  }

  private void openSuite(TacticSuiteSummary suite) {
    Optional<com.escontrela.lastmove.domain.tactics.TacticExerciseId> exerciseId = Optional.empty();
    if (pendingAnalysisSessionId != null) {
      TacticExerciseSummary exercise =
          tacticService.copyAnalysisSessionTactic(
              new CopyAnalysisSessionTacticCommand(
                  ownerId.orElseThrow(),
                  suite.suiteId(),
                  pendingAnalysisSessionId,
                  "Tactic from analysis"));
      exerciseId = Optional.of(exercise.exerciseId());
      pendingAnalysisSessionId = null;
    }
    uiEventBus.publish(new OpenTacticsWorkspaceEvent(suite.suiteId(), exerciseId));
    uiFlowManager.show(UiScreenId.TACTICS_WORKSPACE);
  }

  private final class SuiteCell extends ListCell<TacticSuiteSummary> {
    private final HBox row = new HBox(12);
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label summary = new Label();

    private SuiteCell() {
      row.getStyleClass().add("study-library-row");
      row.setAlignment(Pos.CENTER_LEFT);
      title.getStyleClass().add("study-library-title");
      summary.getStyleClass().add("study-library-summary");
      details.getChildren().addAll(title, summary);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().add(details);
      row.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2
                && getItem() != null) openSuite(getItem());
          });
    }

    @Override
    protected void updateItem(TacticSuiteSummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      title.setText(item.title());
      summary.setText(item.exerciseCount() + (item.exerciseCount() == 1 ? " exercise" : " exercises"));
      setGraphic(row);
    }
  }
}
