package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.TacticService;
import com.escontrela.lastmove.application.tactics.CopyAnalysisSessionTacticCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.DeleteTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.MoveTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.RenameTacticSuiteCommand;
import com.escontrela.lastmove.application.tactics.TacticExerciseSummary;
import com.escontrela.lastmove.application.tactics.TacticSuiteSummary;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.header.ApplicationHeader;
import com.escontrela.lastmove.ui.component.header.HeaderAction;
import com.escontrela.lastmove.ui.component.list.ManagedListCell;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.search.RegexSearchControl;
import com.escontrela.lastmove.ui.component.search.RegexSearchFilter;
import com.escontrela.lastmove.ui.event.OpenTacticsWorkspaceEvent;
import com.escontrela.lastmove.ui.event.SelectTacticDestinationEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
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

  private static final DateTimeFormatter UPDATED_AT =
      DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

  @FXML private StackPane root;
  @FXML private ApplicationHeader applicationHeader;
  @FXML private ListView<TacticSuiteSummary> suiteList;
  @FXML private RegexSearchControl regexSearch;
  @FXML private Label suiteCountLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label statusLabel;
  @FXML private TextInputModal textInputModal;
  @FXML private ContextualMenuPanel contextualMenuPanel;

  private final TacticService tacticService;
  private final CurrentUserService currentUserService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private Optional<PlayerId> ownerId = Optional.empty();
  private List<TacticSuiteSummary> visibleSuites = List.of();
  private List<TacticSuiteSummary> allSuites = List.of();
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
    regexSearch.setOnSearch(event -> showSuites(event.pattern()));
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

  private void showTacticsActions(TacticSuiteSummary summary, double sceneX, double sceneY) {
    int selectedIndex = visibleSuites.indexOf(summary);
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open tactic suite", "", event -> openSuite(summary));
    contextualMenuPanel.addItem("Rename tactic suite…", "", event -> renameSuite(summary));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        "Move tactic suite up", "↑", selectedIndex <= 0, event -> moveSuite(summary, -1));
    contextualMenuPanel.addItem(
        "Move tactic suite down",
        "↓",
        selectedIndex < 0 || selectedIndex >= visibleSuites.size() - 1,
        event -> moveSuite(summary, 1));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete tactic suite…", "", event -> deleteSuite(summary));
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private void renameSuite(TacticSuiteSummary summary) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }

    textInputModal.setTitle("Rename tactic suite");
    textInputModal.setMessage("Choose a clear name for this tactic suite.");
    textInputModal.setPromptText("Tactic suite title");
    textInputModal.setText(summary.title());
    textInputModal.setAcceptText("Rename");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnCancel(event -> statusLabel.setText("Rename cancelled"));
    textInputModal.setOnAccept(
        event -> {
          String title = textInputModal.getText().trim();
          if (title.isEmpty()) {
            textInputModal.setValidationMessage("Enter a tactic suite title.");
            return;
          }
          tacticService.renameSuite(new RenameTacticSuiteCommand(owner, summary.suiteId(), title));
          textInputModal.hide();
          refresh();
          statusLabel.setText("Renamed suite to " + title);
        });
    textInputModal.show();
  }

  private void deleteSuite(TacticSuiteSummary summary) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    tacticService.deleteSuite(new DeleteTacticSuiteCommand(owner, summary.suiteId()));
    refresh();
    statusLabel.setText("Deleted suite: " + summary.title());
  }

  private void moveSuite(TacticSuiteSummary summary, int offset) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    boolean moved = tacticService.moveSuite(new MoveTacticSuiteCommand(owner, summary.suiteId(), offset));
    refresh();
    statusLabel.setText(moved ? "Reordered " + summary.title() : "Suite is already at the edge");
  }

  private void refresh() {
    ownerId = currentUserService.activePlayerState().playerId();
    boolean available = ownerId.isPresent();
    configureCreateSuiteAction(available);
    if (!available) {
      allSuites = List.of();
      visibleSuites = List.of();
      suiteList.getItems().clear();
      suiteCountLabel.setText("0 suites");
      emptyStateLabel.setText("Choose an active player profile to create tactical suites.");
      emptyStateLabel.setVisible(true);
      emptyStateLabel.setManaged(true);
      return;
    }
    allSuites = tacticService.listSuites(ownerId.orElseThrow());
    if (regexSearch.isValid()) {
      regexSearch.submit();
    }
  }

  private void showSuites(java.util.regex.Pattern pattern) {
    visibleSuites = allSuites.stream()
        .filter(suite -> RegexSearchFilter.matches(pattern, suite.title(), suite.description().orElse(""), Integer.toString(suite.exerciseCount())))
        .toList();
    suiteList.getItems().setAll(visibleSuites);
    suiteCountLabel.setText(
        visibleSuites.size() + (visibleSuites.size() == 1 ? " suite" : " suites"));
    emptyStateLabel.setText(allSuites.isEmpty() ? "Create a suite, then add a position and author its solution line." : "No tactic suites match this search.");
    emptyStateLabel.setVisible(visibleSuites.isEmpty());
    emptyStateLabel.setManaged(visibleSuites.isEmpty());
    statusLabel.setText(
        visibleSuites.isEmpty()
            ? "Ready to create your first tactic suite"
            : "Open a suite to train");
  }

  private void configureCreateSuiteAction(boolean available) {
    applicationHeader.setContextActions(
        List.of(
            new HeaderAction(
                "Create tactic suite",
                "Create tactic suite",
                "/images/add_35dp_000000.png",
                "/images/add_35dp_FFFFFFpng.png",
                event -> onCreateSuite(),
                !available)));
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
    uiEventBus.publish(new OpenTacticsWorkspaceEvent(suite.suiteId(), exerciseId, false));
    uiFlowManager.show(UiScreenId.TACTICS_WORKSPACE);
  }

  private final class SuiteCell extends ManagedListCell<TacticSuiteSummary> {
    private final HBox row = new HBox(12);
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label summary = new Label();

    private SuiteCell() {
      getStyleClass().add("study-library-cell");
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
                && getItem() != null) {
              openSuite(getItem());
            }
          });

      row.setOnContextMenuRequested(
          event -> {
            if (getItem() != null) {
              showTacticsActions(getItem(), event.getSceneX(), event.getSceneY());
              event.consume();
            }
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

      String description =
          item.description().filter(value -> !value.isBlank()).orElse("No description");
      summary.setText(
          item.exerciseCount()
              + (item.exerciseCount() == 1 ? " exercise · " : " exercises · ")
              + description
              + " · updated "
              + UPDATED_AT.format(item.updatedAt()));

      setGraphic(row);
    }
  }
}
