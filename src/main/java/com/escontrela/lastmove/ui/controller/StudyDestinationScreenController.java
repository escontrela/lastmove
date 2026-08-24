package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisSessionSummary;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.study.CopySessionChapterCommand;
import com.escontrela.lastmove.application.study.CreateStudyCommand;
import com.escontrela.lastmove.application.study.StudyChapterSummary;
import com.escontrela.lastmove.application.study.StudySummary;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.event.SelectStudyDestinationEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Selects the persistent-study destination for a copied ephemeral analysis session. */
@Component
public final class StudyDestinationScreenController implements UiScreenController {

  @FXML private VBox root;
  @FXML private Label sessionTitleLabel;
  @FXML private Label emptyStateLabel;
  @FXML private Label statusLabel;
  @FXML private ListView<StudySummary> studyList;
  @FXML private Button createStudyButton;
  @FXML private TextInputModal textInputModal;

  private final StudyService studyService;
  private final AnalysisSessionService analysisSessionService;
  private final CurrentUserService currentUserService;
  private final UiEventBus uiEventBus;
  private final UiFlowManager uiFlowManager;
  private AnalysisSessionId pendingSessionId;
  private SelectStudyDestinationEvent.PostCopyDestination postCopyDestination =
      SelectStudyDestinationEvent.PostCopyDestination.RETURN_TO_ANALYSIS;
  private Optional<PlayerId> ownerId = Optional.empty();

  public StudyDestinationScreenController(
      StudyService studyService,
      AnalysisSessionService analysisSessionService,
      CurrentUserService currentUserService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.studyService = studyService;
    this.analysisSessionService = analysisSessionService;
    this.currentUserService = currentUserService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    studyList.setCellFactory(ignored -> new DestinationStudyCell());
  }

  @EventListener
  public void onSelectStudyDestination(SelectStudyDestinationEvent event) {
    pendingSessionId = event.sessionId();
    postCopyDestination = event.postCopyDestination();
  }

  @Override
  public void onShow() {
    if (pendingSessionId == null || currentUserService.activePlayerState().playerId().isEmpty()) {
      returnToAnalysis("Study destination selection cancelled");
      return;
    }
    ownerId = currentUserService.activePlayerState().playerId();
    AnalysisSessionSummary session = analysisSessionService.sessionSummary(pendingSessionId);
    sessionTitleLabel.setText("Add “" + session.title() + "” as a chapter");
    List<StudySummary> studies = studyService.listStudies(ownerId.orElseThrow());
    studyList.getItems().setAll(studies);
    studyList.setPrefHeight(Math.max(92.0, Math.min(380.0, studies.size() * 76.0 + 2.0)));
    emptyStateLabel.setVisible(studies.isEmpty());
    emptyStateLabel.setManaged(studies.isEmpty());
    statusLabel.setText(studies.isEmpty() ? "Create a study to save this session" : "Choose a destination study");
  }

  @FXML
  public void onCreateStudy() {
    AnalysisSessionSummary session = pendingSession();
    textInputModal.setTitle("New study");
    textInputModal.setMessage("The active session will become its first chapter.");
    textInputModal.setPromptText("Study title");
    textInputModal.setText(session.title());
    textInputModal.setAcceptText("Create and add");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(event -> createAndCopy(textInputModal.getText()));
    textInputModal.setOnCancel(event -> statusLabel.setText("Study creation cancelled"));
    textInputModal.show();
  }

  @FXML
  public void backToAnalysis() {
    returnToAnalysis("Study destination selection cancelled");
  }

  private void createAndCopy(String requestedTitle) {
    String title = requestedTitle.trim();
    if (title.isEmpty()) {
      textInputModal.setValidationMessage("Enter a study title.");
      return;
    }
    PlayerId owner = ownerId.orElseThrow();
    try {
      StudySummary study = studyService.createStudy(new CreateStudyCommand(owner, title, Optional.empty()));
      copyToStudy(study);
      textInputModal.hide();
    } catch (RuntimeException exception) {
      textInputModal.setValidationMessage(messageOf(exception, "Unable to create study."));
    }
  }

  private void copyToStudy(StudySummary study) {
    PlayerId owner = ownerId.orElseThrow();
    AnalysisSessionSummary session = pendingSession();
    try {
      StudyChapterSummary chapter = studyService.copySessionChapter(
          new CopySessionChapterCommand(owner, study.studyId(), session.title(), pendingSessionId));
      if (postCopyDestination
          == SelectStudyDestinationEvent.PostCopyDestination.OPEN_STUDY_WORKSPACE) {
        uiEventBus.publish(new OpenStudyWorkspaceEvent(study.studyId(), chapter.chapterId()));
        uiFlowManager.show(UiScreenId.STUDY_WORKSPACE);
      } else {
        returnToAnalysis("Added “" + session.title() + "” to " + study.title());
      }
    } catch (RuntimeException exception) {
      statusLabel.setText(messageOf(exception, "Unable to add the session as a chapter."));
    }
  }

  private AnalysisSessionSummary pendingSession() {
    return analysisSessionService.sessionSummary(pendingSessionId);
  }

  private void returnToAnalysis(String status) {
    if (pendingSessionId != null) {
      uiEventBus.publish(new OpenAnalysisSessionEvent(pendingSessionId, status));
    }
    uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
  }

  private static String messageOf(RuntimeException exception, String fallback) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
        ? fallback
        : exception.getMessage();
  }

  private final class DestinationStudyCell extends ListCell<StudySummary> {

    private final HBox row = new HBox(12);
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label summary = new Label();
    private final Button choose = new Button("Add chapter");

    private DestinationStudyCell() {
      getStyleClass().add("study-library-cell");
      row.getStyleClass().add("study-destination-row");
      row.setAlignment(Pos.CENTER_LEFT);
      title.getStyleClass().add("study-library-title");
      summary.getStyleClass().add("study-library-summary");
      details.getChildren().addAll(title, summary);
      details.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(details, Priority.ALWAYS);
      choose.getStyleClass().addAll("session-action-button", "study-open-button");
      row.getChildren().addAll(details, choose);
      choose.setOnAction(event -> copyToStudy(getItem()));
    }

    @Override
    protected void updateItem(StudySummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      title.setText(item.title());
      summary.setText(item.chapterCount() + (item.chapterCount() == 1 ? " chapter" : " chapters"));
      setGraphic(row);
    }
  }
}
