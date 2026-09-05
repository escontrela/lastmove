package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.CurrentUserService.ActivePlayerState;
import com.escontrela.lastmove.application.service.CurrentUserService.ActivePlayerStatus;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.service.TagService;
import com.escontrela.lastmove.application.tag.Tag;
import com.escontrela.lastmove.application.study.CreateChapterCommand;
import com.escontrela.lastmove.application.study.CreateStudyCommand;
import com.escontrela.lastmove.application.study.DeleteStudyCommand;
import com.escontrela.lastmove.application.study.MoveStudyCommand;
import com.escontrela.lastmove.application.study.RenameStudyCommand;
import com.escontrela.lastmove.application.study.StudyDetails;
import com.escontrela.lastmove.application.study.StudySummary;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.header.ApplicationHeader;
import com.escontrela.lastmove.ui.component.header.HeaderAction;
import com.escontrela.lastmove.ui.component.list.ManagedListCell;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.search.RegexSearchControl;
import com.escontrela.lastmove.ui.component.search.RegexSearchFilter;
import com.escontrela.lastmove.ui.component.tag.TagAssignmentControl;
import com.escontrela.lastmove.ui.component.tag.TagDisplayControl;
import com.escontrela.lastmove.ui.component.tag.TagFilterControl;
import com.escontrela.lastmove.ui.component.toolbar.ThemeIcon;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Library screen for the persisted studies owned by the selected local player. */
@Component
public final class StudiesScreenController implements UiScreenController {

  private static final DateTimeFormatter UPDATED_AT =
      DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

  @FXML private StackPane root;
  @FXML private ApplicationHeader applicationHeader;
  @FXML private ListView<StudySummary> studyList;
  @FXML private RegexSearchControl regexSearch;
  @FXML private TagFilterControl tagFilter;
  @FXML private Label profileStateLabel;
  @FXML private Label studyCountLabel, studyLibraryCountLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label statusLabel;
  @FXML private Button createStudyButton;
  @FXML private TextInputModal textInputModal;
  @FXML private ContextualMenuPanel contextualMenuPanel;

  private final StudyService studyService;
  private final TagService tagService;
  private final CurrentUserService currentUserService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private Optional<PlayerId> ownerId = Optional.empty();
  private List<StudySummary> visibleStudies = List.of();
  private List<StudySummary> allStudies = List.of();
  private List<Tag> availableTags = List.of();
  private Map<StudyId, List<Tag>> tagsByStudy = Map.of();

  public StudiesScreenController(
      StudyService studyService,
      TagService tagService,
      CurrentUserService currentUserService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.studyService = studyService;
    this.tagService = tagService;
    this.currentUserService = currentUserService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    studyList.setCellFactory(ignored -> new StudyCell());
    regexSearch.setOnSearch(event -> showStudies(event.pattern()));
    tagFilter.setOnSelectionChanged(ignored -> { if (regexSearch.isValid()) regexSearch.submit(); });
  }

  @Override
  public void onShow() {
    refreshLibrary();
  }

  @FXML
  public void onCreateStudy() {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      statusLabel.setText("Select a player profile before creating a study.");
      return;
    }
    textInputModal.setTitle("Create study");
    textInputModal.setMessage("Give this study a concise, recognizable title.");
    textInputModal.setPromptText("Study title");
    textInputModal.setText("");
    textInputModal.setAcceptText("Create study");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnCancel(event -> statusLabel.setText("Study creation cancelled"));
    textInputModal.setOnAccept(event -> createStudy(owner, textInputModal.getText()));
    textInputModal.show();
  }

  @FXML
  public void backToMain() {
    uiFlowManager.show(UiScreenId.MAIN);
  }

  private void createStudy(PlayerId owner, String requestedTitle) {
    String title = requestedTitle.trim();
    if (title.isEmpty()) {
      textInputModal.setValidationMessage("Enter a study title.");
      return;
    }
    try {
      StudySummary study =
          studyService.createStudy(new CreateStudyCommand(owner, title, Optional.empty()));
      StudyChapterId chapterId =
          studyService
              .createChapter(new CreateChapterCommand(owner, study.studyId(), "Chapter 1"))
              .chapterId();
      textInputModal.hide();
      openWorkspace(study.studyId(), chapterId);
    } catch (RuntimeException exception) {
      textInputModal.setValidationMessage(messageOf(exception, "Unable to create study."));
    }
  }

  private void refreshLibrary() {

    ActivePlayerState state = currentUserService.activePlayerState();
    ownerId = state.playerId();
    boolean available = state.status() == ActivePlayerStatus.ACTIVE;
    createStudyButton.setDisable(!available);
    configureCreateStudyAction(available);

    if (!available) {

      allStudies = List.of();
      visibleStudies = List.of();
      studyList.getItems().clear();
      studyList.setPrefHeight(116.0);
      studyCountLabel.setText("0 studies");
      studyLibraryCountLabel.setText("0 studies");
      emptyStateLabel.setText(
          state.status() == ActivePlayerStatus.NO_PROFILE
              ? "Choose an active player profile to create and open studies."
              : "Study persistence is unavailable right now.");
      emptyStateLabel.setVisible(true);
      emptyStateLabel.setManaged(true);
      profileStateLabel.setText(
          state.status() == ActivePlayerStatus.NO_PROFILE
              ? "No active player"
              : "Persistence unavailable");
      statusLabel.setText("Studies require an active player profile.");

      return;
    }

    allStudies = studyService.listStudies(ownerId.orElseThrow());
    tagsByStudy = tagService.tagsForStudies(allStudies.stream().map(StudySummary::studyId).toList());
    availableTags = tagService.availableTags();
    tagFilter.setAvailableTags(availableTags);
    if (regexSearch.isValid()) {
      regexSearch.submit();
    }
  }

  private void showStudies(java.util.regex.Pattern pattern) {
    Set<Long> selectedTags = tagFilter.selectedTagIds();
    visibleStudies = allStudies.stream()
        .filter(study -> RegexSearchFilter.matches(pattern, study.title(), study.description().orElse(""), Integer.toString(study.chapterCount())))
        .filter(study -> tagsByStudy.getOrDefault(study.studyId(), List.of()).stream().map(Tag::id).collect(java.util.stream.Collectors.toSet()).containsAll(selectedTags))
        .toList();
    studyList.getItems().setAll(visibleStudies);
    studyList.setPrefHeight(Math.max(92.0, Math.min(468.0, visibleStudies.size() * 82.0 + 2.0)));
    studyCountLabel.setText(
        visibleStudies.size() + (visibleStudies.size() == 1 ? " study" : " studies"));
    studyLibraryCountLabel.setText(
        visibleStudies.size() + (visibleStudies.size() == 1 ? " study" : " studies"));
    emptyStateLabel.setText(allStudies.isEmpty() ? "Create a study to start collecting persistent chapters." : "No studies match this search.");
    emptyStateLabel.setVisible(visibleStudies.isEmpty());
    emptyStateLabel.setManaged(visibleStudies.isEmpty());
    profileStateLabel.setText("Studies for active player");
    statusLabel.setText(
        visibleStudies.isEmpty()
            ? "Ready to create your first study"
            : "Choose a study to continue");
  }

  private void configureCreateStudyAction(boolean available) {
    applicationHeader.setContextActions(
        List.of(
            new HeaderAction(
                "Create study",
                "Create study",
                "/images/add_35dp_000000.png",
                "/images/add_35dp_FFFFFF.png",
                event -> onCreateStudy(),
                !available)));
  }

  private void openStudy(StudySummary summary) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    StudyDetails details = studyService.studyDetails(owner, summary.studyId());
    StudyChapterId chapterId;
    if (details.chapters().isEmpty()) {
      chapterId =
          studyService
              .createChapter(new CreateChapterCommand(owner, summary.studyId(), "Chapter 1"))
              .chapterId();
    } else {
      chapterId = details.chapters().getFirst().chapterId();
    }
    openWorkspace(summary.studyId(), chapterId);
  }

  private void openWorkspace(StudyId studyId, StudyChapterId chapterId) {
    uiEventBus.publish(new OpenStudyWorkspaceEvent(studyId, chapterId));
    uiFlowManager.show(UiScreenId.STUDY_WORKSPACE);
  }

  private void renameStudy(StudySummary summary) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    textInputModal.setTitle("Rename study");
    textInputModal.setMessage("Choose a clear name for this study.");
    textInputModal.setPromptText("Study title");
    textInputModal.setText(summary.title());
    textInputModal.setAcceptText("Rename");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnCancel(event -> statusLabel.setText("Rename cancelled"));
    textInputModal.setOnAccept(
        event -> {
          String title = textInputModal.getText().trim();
          if (title.isEmpty()) {
            textInputModal.setValidationMessage("Enter a study title.");
            return;
          }
          studyService.renameStudy(new RenameStudyCommand(owner, summary.studyId(), title));
          textInputModal.hide();
          refreshLibrary();
          statusLabel.setText("Renamed study to " + title);
        });
    textInputModal.show();
  }

  private void deleteStudy(StudySummary summary) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    studyService.deleteStudy(new DeleteStudyCommand(owner, summary.studyId()));
    refreshLibrary();
    statusLabel.setText("Deleted study: " + summary.title());
  }

  private void moveStudy(StudySummary summary, int offset) {
    PlayerId owner = ownerId.orElse(null);
    if (owner == null) {
      return;
    }
    boolean moved = studyService.moveStudy(new MoveStudyCommand(owner, summary.studyId(), offset));
    refreshLibrary();
    statusLabel.setText(moved ? "Reordered " + summary.title() : "Study is already at the edge");
  }

  private void showStudyActions(StudySummary summary, double sceneX, double sceneY) {
    int selectedIndex = visibleStudies.indexOf(summary);
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open study", "", event -> openStudy(summary));
    contextualMenuPanel.addItem("Rename study…", "", event -> renameStudy(summary));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        "Move study up", "↑", selectedIndex <= 0, event -> moveStudy(summary, -1));
    contextualMenuPanel.addItem(
        "Move study down",
        "↓",
        selectedIndex < 0 || selectedIndex >= visibleStudies.size() - 1,
        event -> moveStudy(summary, 1));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete study…", "", event -> deleteStudy(summary));
    contextualMenuPanel.addSeparator();
    TagAssignmentControl tags = new TagAssignmentControl();
    tags.setAvailableTags(availableTags);
    tags.setAssignedTags(tagsByStudy.getOrDefault(summary.studyId(), List.of()));
    tags.setOnAssign(name -> {
      try {
        tagService.assignToStudy(summary.studyId(), name);
        refreshLibrary();
        tags.setAvailableTags(availableTags);
        tags.setAssignedTags(tagsByStudy.getOrDefault(summary.studyId(), List.of()));
        statusLabel.setText("Added tag to " + summary.title());
      } catch (IllegalArgumentException failure) {
        statusLabel.setText(failure.getMessage());
      }
    });
    tags.setOnRemove(tag -> {
      tagService.removeFromStudy(summary.studyId(), tag.id());
      refreshLibrary();
      tags.setAssignedTags(tagsByStudy.getOrDefault(summary.studyId(), List.of()));
      statusLabel.setText("Removed tag " + tag.name());
    });
    contextualMenuPanel.addContent(tags);
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private static String messageOf(RuntimeException exception, String fallback) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? fallback
        : exception.getMessage();
  }

  private final class StudyCell extends ManagedListCell<StudySummary> {

    private final HBox row = new HBox(12);
    private final ThemeIcon studyIcon = new ThemeIcon();
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label summary = new Label();
    private final TagDisplayControl tags = new TagDisplayControl();
    private final Label chapters = new Label();
    private final Label updated = new Label();
    private final Button action = new Button("Open");

    private StudyCell() {
      getStyleClass().add("study-library-cell");
      row.getStyleClass().add("study-library-row");
      row.getStyleClass().add("tactics-library-row");
      row.getStyleClass().add("study-library-row-with-icon");
      row.setAlignment(Pos.CENTER_LEFT);
      studyIcon.setFitWidth(28.0);
      studyIcon.setFitHeight(28.0);
      studyIcon.setLightIconResource("/images/menu_book_35dp_000000.png");
      studyIcon.setDarkIconResource("/images/menu_book_35dp_FFFFFF.png");
      title.getStyleClass().add("study-library-title");
      summary.getStyleClass().add("study-library-summary");
      chapters.getStyleClass().add("my-games-moves");
      updated.getStyleClass().add("my-games-updated");
      action.getStyleClass().add("my-games-open-button");
      action.setOnAction(event -> { if (getItem() != null) openStudy(getItem()); });
      details.getChildren().addAll(title, summary, tags);
      HBox.setHgrow(details, Priority.ALWAYS);
      details.setMaxWidth(Double.MAX_VALUE);
      row.getChildren().addAll(studyIcon, details, chapters, updated, action);
      row.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY
                && event.getClickCount() == 2
                && getItem() != null) {
              openStudy(getItem());
            }
          });
      row.setOnContextMenuRequested(
          event -> {
            if (getItem() != null) {
              showStudyActions(getItem(), event.getSceneX(), event.getSceneY());
              event.consume();
            }
          });
    }

    @Override
    protected void updateItem(StudySummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      title.setText(item.title());
      String description =
          item.description().filter(value -> !value.isBlank()).orElse("No description");
      summary.setText(
          item.chapterCount()
              + (item.chapterCount() == 1 ? " chapter · " : " chapters · ")
              + description
              + " · updated "
              + UPDATED_AT.format(item.updatedAt()));
      tags.setTags(tagsByStudy.getOrDefault(item.studyId(), List.of()));
      chapters.setText(Integer.toString(item.chapterCount()));
      updated.setText(UPDATED_AT.format(item.updatedAt()));
      setGraphic(row);
    }
  }
}
