package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.TacticService;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.tactics.AppendTacticSolutionMoveCommand;
import com.escontrela.lastmove.application.tactics.CreateTacticExerciseFromFenCommand;
import com.escontrela.lastmove.application.tactics.DeleteTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.MoveTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.RenameTacticExerciseCommand;
import com.escontrela.lastmove.application.tactics.TacticAuthoringMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticExerciseSummary;
import com.escontrela.lastmove.application.tactics.TacticHint;
import com.escontrela.lastmove.application.tactics.TacticMoveOutcome;
import com.escontrela.lastmove.application.tactics.TacticPositionEditContext;
import com.escontrela.lastmove.application.tactics.TacticSuiteDetails;
import com.escontrela.lastmove.application.tactics.TacticWorkspace;
import com.escontrela.lastmove.application.tactics.TemporaryTacticSession;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.event.OpenTacticsWorkspaceEvent;
import com.escontrela.lastmove.ui.event.OpenTacticPositionEditorEvent;
import com.escontrela.lastmove.ui.event.OpenStudyChapterTacticEvent;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionTacticEvent;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.fxml.FXML;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Board workspace for authoring a local tactic solution or solving an existing exercise. */
@Component
public final class TacticsWorkspaceScreenController implements UiScreenController {

  @FXML private StackPane root;
  @FXML private ChessBoardControl chessBoard;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private TextInputModal textInputModal;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private ListView<TacticExerciseSummary> exerciseList;
  @FXML private Label suiteTitleLabel;
  @FXML private Label exerciseTitleLabel;
  @FXML private Label modeLabel;
  @FXML private Label statusLabel;
  @FXML private VBox resultPanel;
  @FXML private Label resultScoreLabel;
  @FXML private Label resultDetailLabel;
  @FXML private Button hintButton;
  @FXML private Button nextExerciseButton;
  @FXML private Button backToStudyButton;
  @FXML private VBox exerciseRail;

  private final TacticService tacticService;
  private final StudyService studyService;
  private final AnalysisSessionService analysisSessionService;
  private final CurrentUserService currentUserService;
  private final ChessSoundService chessSoundService;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final UiFlowManager uiFlowManager;
  private final UiEventBus uiEventBus;
  private TacticSuiteId activeSuiteId;
  private TacticExerciseId activeExerciseId;
  private Optional<AnalysisNodeId> authorParentNodeId = Optional.empty();
  private BoardMoveInput pendingPromotionMove;
  private boolean authoring;
  private UUID temporarySessionId;
  private StudyId temporaryStudyId;
  private StudyChapterId temporaryChapterId;
  private AnalysisSessionId temporaryAnalysisSessionId;
  private TacticWorkspace temporaryWorkspace;

  public TacticsWorkspaceScreenController(
      TacticService tacticService,
      StudyService studyService,
      AnalysisSessionService analysisSessionService,
      CurrentUserService currentUserService,
      ChessSoundService chessSoundService,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.tacticService = tacticService;
    this.studyService = studyService;
    this.analysisSessionService = analysisSessionService;
    this.currentUserService = currentUserService;
    this.chessSoundService = chessSoundService;
    this.boardAppearancePreferencesService = boardAppearancePreferencesService;
    this.uiEventBus = uiEventBus;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    chessSoundService.preload();
    chessBoard.setSoundService(chessSoundService);
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    chessBoard.appearancePresetProperty().bind(
        boardAppearancePreferencesService.boardAppearancePresetProperty());
    exerciseList.setCellFactory(ignored -> new ExerciseCell());
    chessBoard.setOnMoveRequested(event -> submitMove(event.getMoveInput()));
    chessBoard.setOnPromotionRequested(
        event -> {
          pendingPromotionMove = event.getMoveInput();
          promotionPicker.showFor(event.getPromotingColor());
        });
    promotionPicker.setOnPromotionSelected(
        event -> {
          if (pendingPromotionMove != null) {
            BoardMoveInput move = pendingPromotionMove.withPromotion(event.pieceType());
            pendingPromotionMove = null;
            chessBoard.handleBoardMoveInput(move);
          }
        });
    promotionPicker.setOnCancel(event -> pendingPromotionMove = null);
  }

  @EventListener
  public void onOpenTacticsWorkspace(OpenTacticsWorkspaceEvent event) {
    clearTemporaryState();
    activeSuiteId = event.suiteId();
    activeExerciseId = event.exerciseId().orElse(null);
    authoring = event.authoring();
    authorParentNodeId = Optional.empty();
  }

  @EventListener
  public void onOpenStudyChapterTactic(OpenStudyChapterTacticEvent event) {
    clearTemporaryState();
    Optional<PlayerId> owner = activeOwner();
    if (owner.isEmpty()) return;
    var source = studyService.chapterTacticSource(owner.orElseThrow(), event.studyId(), event.chapterId());
    TemporaryTacticSession session = tacticService.startTemporaryExercise(source.title(), source.document());
    temporarySessionId = session.sessionId();
    temporaryStudyId = event.studyId();
    temporaryChapterId = event.chapterId();
    temporaryWorkspace = session.workspace();
    activeSuiteId = null;
    activeExerciseId = null;
    authoring = false;
    authorParentNodeId = Optional.empty();
  }

  @EventListener
  public void onOpenAnalysisSessionTactic(OpenAnalysisSessionTacticEvent event) {
    clearTemporaryState();
    var source = analysisSessionService.sessionTacticSource(event.sessionId());
    TemporaryTacticSession session = tacticService.startTemporaryExercise(source.title(), source.document());
    temporarySessionId = session.sessionId();
    temporaryAnalysisSessionId = event.sessionId();
    temporaryWorkspace = session.workspace();
    activeSuiteId = null;
    activeExerciseId = null;
    authoring = false;
    authorParentNodeId = Optional.empty();
  }

  @Override
  public void onShow() {
    if (isTemporaryExercise()) {
      showTemporaryExercise();
      return;
    }
    if (activeSuiteId == null || activeOwner().isEmpty()) {
      uiFlowManager.show(UiScreenId.TACTICS);
      return;
    }
    refreshSuite();
  }

  @FXML
  public void backToSuites() {
    if (isTemporaryExercise()) {
      StudyId studyId = temporaryStudyId;
      StudyChapterId chapterId = temporaryChapterId;
      AnalysisSessionId analysisSessionId = temporaryAnalysisSessionId;
      clearTemporaryState();
      if (analysisSessionId != null) {
        uiEventBus.publish(
            new OpenAnalysisSessionEvent(analysisSessionId, "Returned from tactic practice"));
        uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
      } else {
        uiEventBus.publish(new OpenStudyWorkspaceEvent(studyId, chapterId));
        uiFlowManager.show(UiScreenId.STUDY_WORKSPACE);
      }
      return;
    }
    uiFlowManager.show(UiScreenId.TACTICS);
  }

  @FXML
  public void onAddFromFen() {
    textInputModal.setTitle("New tactic from FEN");
    textInputModal.setMessage("Paste the position. You will then author the accepted solution on the board.");
    textInputModal.setPromptText("FEN position");
    textInputModal.setText("");
    textInputModal.setAcceptText("Create tactic");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(event -> createFromFen(textInputModal.getText()));
    textInputModal.show();
  }

  /** Opens the position editor to compose the starting position of a new tactic exercise. */
  @FXML
  public void onAddFromEditor() {
    Optional<PlayerId> owner = activeOwner();
    if (owner.isEmpty()) return;
    uiEventBus.publish(
        new OpenTacticPositionEditorEvent(
            new TacticPositionEditContext(owner.orElseThrow(), activeSuiteId)));
    uiFlowManager.show(UiScreenId.POSITION_EDITOR);
  }

  @FXML
  public void onToggleAuthoring() {
    if (isTemporaryExercise()) return;
    if (activeExerciseId == null) return;
    authoring = !authoring;
    authorParentNodeId = Optional.empty();
    render(tacticService.startExercise(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId));
    statusLabel.setText(authoring ? "Authoring solution: add the accepted line from this position." : "Training mode ready.");
  }

  @FXML
  public void onRestart() {
    if (isTemporaryExercise()) {
      chessBoard.clearHintSquare();
      temporaryWorkspace = tacticService.restartTemporaryExercise(temporarySessionId);
      render(temporaryWorkspace);
      return;
    }
    if (activeExerciseId != null) {
      authorParentNodeId = Optional.empty();
      chessBoard.clearHintSquare();
      render(tacticService.startExercise(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId));
    }
  }

  @FXML
  public void onHint() {
    if (isTemporaryExercise()) {
      TacticHint hint = tacticService.requestTemporaryHint(temporarySessionId);
      chessBoard.setHintSquare(hint.sourceSquare().orElse(null));
      temporaryWorkspace = hint.workspace();
      render(temporaryWorkspace);
      return;
    }
    if (activeExerciseId == null || authoring) return;
    TacticHint hint =
        tacticService.requestHint(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId);
    chessBoard.setHintSquare(hint.sourceSquare().orElse(null));
    render(hint.workspace());
  }

  @FXML
  public void onNextExercise() {
    if (isTemporaryExercise()) return;
    if (activeExerciseId == null) return;
    int currentIndex =
        java.util.stream.IntStream.range(0, exerciseList.getItems().size())
            .filter(index -> exerciseList.getItems().get(index).exerciseId().equals(activeExerciseId))
            .findFirst()
            .orElse(-1);
    if (currentIndex >= 0 && currentIndex + 1 < exerciseList.getItems().size()) {
      activate(exerciseList.getItems().get(currentIndex + 1));
    }
  }

  @FXML
  public void onRotateBoard() {
    chessBoard.toggleOrientation();
  }

  private void createFromFen(String rawFen) {
    try {
      TacticExerciseSummary exercise =
          tacticService.createExerciseFromFen(
              new CreateTacticExerciseFromFenCommand(
                  activeOwner().orElseThrow(), activeSuiteId, "New tactic", Fen.of(rawFen.trim())));
      textInputModal.hide();
      activeExerciseId = exercise.exerciseId();
      authoring = true;
      authorParentNodeId = Optional.empty();
      refreshSuite();
      statusLabel.setText("Tactic created. Add its solution moves on the board.");
    } catch (RuntimeException exception) {
      textInputModal.setValidationMessage(
          exception.getMessage() == null ? "The FEN position is invalid." : exception.getMessage());
    }
  }

  private void refreshSuite() {
    TacticSuiteDetails details = tacticService.suiteDetails(activeOwner().orElseThrow(), activeSuiteId);
    suiteTitleLabel.setText(details.suite().title());
    List<TacticExerciseSummary> exercises = details.exercises();
    exerciseList.getItems().setAll(exercises);
    if (activeExerciseId == null && !exercises.isEmpty()) activeExerciseId = exercises.getFirst().exerciseId();
    if (activeExerciseId == null) {
      exerciseTitleLabel.setText("No tactic selected");
      modeLabel.setText("Add a FEN position to begin");
      statusLabel.setText("This suite is empty.");
      chessBoard.setDisable(true);
      return;
    }
    render(tacticService.startExercise(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId));
    exerciseList.refresh();
  }

  private void activate(TacticExerciseSummary exercise) {
    activeExerciseId = exercise.exerciseId();
    authoring = false;
    authorParentNodeId = Optional.empty();
    chessBoard.clearHintSquare();
    render(tacticService.startExercise(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId));
    exerciseList.refresh();
  }

  private void renameExercise(TacticExerciseSummary exercise) {
    textInputModal.setTitle("Rename tactic");
    textInputModal.setMessage("Choose a clear title for this exercise.");
    textInputModal.setPromptText("Tactic title");
    textInputModal.setText(exercise.title());
    textInputModal.setAcceptText("Rename");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(
        event -> {
          String title = textInputModal.getText().trim();
          if (title.isEmpty()) {
            textInputModal.setValidationMessage("Enter a tactic title.");
            return;
          }
          tacticService.renameExercise(
              new RenameTacticExerciseCommand(
                  activeOwner().orElseThrow(), activeSuiteId, exercise.exerciseId(), title));
          textInputModal.hide();
          refreshSuite();
          statusLabel.setText("Renamed tactic to " + title);
        });
    textInputModal.show();
  }

  private void moveExercise(TacticExerciseSummary exercise, int offset) {
    boolean moved =
        tacticService.moveExercise(
            new MoveTacticExerciseCommand(
                activeOwner().orElseThrow(), activeSuiteId, exercise.exerciseId(), offset));
    refreshSuite();
    statusLabel.setText(moved ? "Reordered " + exercise.title() : "Tactic is already at the edge");
  }

  private void deleteExercise(TacticExerciseSummary exercise) {
    tacticService.deleteExercise(
        new DeleteTacticExerciseCommand(
            activeOwner().orElseThrow(), activeSuiteId, exercise.exerciseId()));
    if (exercise.exerciseId().equals(activeExerciseId)) {
      activeExerciseId = null;
      authoring = false;
      authorParentNodeId = Optional.empty();
    }
    refreshSuite();
    statusLabel.setText("Deleted tactic: " + exercise.title());
  }

  private void showExerciseActions(TacticExerciseSummary exercise, double sceneX, double sceneY) {
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Train tactic", "", event -> activate(exercise));
    contextualMenuPanel.addItem("Rename tactic…", "", event -> renameExercise(exercise));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Move tactic up", "↑", event -> moveExercise(exercise, -1));
    contextualMenuPanel.addItem("Move tactic down", "↓", event -> moveExercise(exercise, 1));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete tactic…", "", event -> deleteExercise(exercise));
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private void submitMove(BoardMoveInput input) {
    if (isTemporaryExercise()) {
      chessBoard.clearHintSquare();
      TacticMoveOutcome outcome =
          tacticService.attemptTemporaryMove(
              temporarySessionId, new MoveCommand(input.fromSquare(), input.toSquare(), input.promotionPiece()));
      temporaryWorkspace = outcome.workspace();
      render(temporaryWorkspace);
      return;
    }
    if (activeExerciseId == null) return;
    chessBoard.clearHintSquare();
    MoveCommand move = new MoveCommand(input.fromSquare(), input.toSquare(), input.promotionPiece());
    if (authoring) {
      TacticAuthoringMoveOutcome outcome =
          tacticService.appendSolutionMove(
              new AppendTacticSolutionMoveCommand(
                  activeOwner().orElseThrow(), activeSuiteId, activeExerciseId, authorParentNodeId, move));
      if (outcome.accepted()) authorParentNodeId = outcome.nodeId();
      render(outcome.workspace());
      return;
    }
    TacticMoveOutcome outcome =
        tacticService.attemptMove(activeOwner().orElseThrow(), activeSuiteId, activeExerciseId, move);
    render(outcome.workspace());
  }

  private void render(TacticWorkspace workspace) {
    exerciseTitleLabel.setText(workspace.exerciseTitle());
    modeLabel.setText(
        authoring
            ? "Authoring solution line"
            : workspace.readyToSolve()
                ? "Solve as " + colorName(workspace.solverColor())
                : "Solution needed");
    chessBoard.setDisable(!authoring && !workspace.readyToSolve());
    hintButton.setDisable(authoring || !workspace.readyToSolve() || workspace.solved());
    chessBoard.setFlipped(!authoring && workspace.solverColor() == PieceColor.BLACK);
    chessBoard.renderPosition(workspace.position());
    statusLabel.setText(workspace.status());
    renderResult(workspace);
  }

  private boolean isTemporaryExercise() {
    return temporarySessionId != null;
  }

  private void showTemporaryExercise() {
    exerciseRail.setManaged(false);
    exerciseRail.setVisible(false);
    backToStudyButton.setManaged(true);
    backToStudyButton.setVisible(true);
    suiteTitleLabel.setText("Study chapter tactic");
    exerciseList.getItems().clear();
    render(temporaryWorkspace);
  }

  private void clearTemporaryState() {
    if (temporarySessionId != null) tacticService.closeTemporaryExercise(temporarySessionId);
    temporarySessionId = null;
    temporaryStudyId = null;
    temporaryChapterId = null;
    temporaryAnalysisSessionId = null;
    temporaryWorkspace = null;
    if (exerciseRail != null) {
      exerciseRail.setManaged(true);
      exerciseRail.setVisible(true);
    }
    if (backToStudyButton != null) {
      backToStudyButton.setManaged(false);
      backToStudyButton.setVisible(false);
    }
  }

  private void renderResult(TacticWorkspace workspace) {
    boolean showResult = !authoring && workspace.solved();
    resultPanel.setManaged(showResult);
    resultPanel.setVisible(showResult);
    if (!showResult) return;

    int score = workspace.accuracyPercentage();
    resultScoreLabel.setText(score + "%");
    resultScoreLabel.getStyleClass().removeAll("score-high", "score-medium", "score-low");
    resultScoreLabel.getStyleClass().add(score > 80 ? "score-high" : score >= 50 ? "score-medium" : "score-low");
    resultDetailLabel.setText(
        workspace.correctMoves()
            + "/"
            + workspace.attemptedMoves()
            + " correct"
            + (workspace.hintCount() == 0
                ? ""
                : " · " + workspace.hintCount() + " hint" + (workspace.hintCount() == 1 ? "" : "s")));
    boolean hasNext =
        java.util.stream.IntStream.range(0, exerciseList.getItems().size())
            .anyMatch(
                index ->
                    exerciseList.getItems().get(index).exerciseId().equals(activeExerciseId)
                        && index + 1 < exerciseList.getItems().size());
    nextExerciseButton.setDisable(!hasNext);
    playResultAnimation();
  }

  private void playResultAnimation() {
    resultPanel.setOpacity(0);
    resultPanel.setScaleX(0.94);
    resultPanel.setScaleY(0.94);
    FadeTransition fade = new FadeTransition(Duration.millis(220), resultPanel);
    fade.setToValue(1);
    ScaleTransition scale = new ScaleTransition(Duration.millis(220), resultPanel);
    scale.setToX(1);
    scale.setToY(1);
    new ParallelTransition(fade, scale).play();
  }

  private Optional<PlayerId> activeOwner() {
    return currentUserService.activePlayerState().playerId();
  }

  private String colorName(PieceColor color) {
    return color == PieceColor.WHITE ? "White" : "Black";
  }

  private final class ExerciseCell extends ListCell<TacticExerciseSummary> {
    private final HBox row = new HBox(12);
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label summary = new Label();

    private ExerciseCell() {
      row.getStyleClass().add("chapter-row");
      row.setAlignment(Pos.CENTER_LEFT);
      title.getStyleClass().add("chapter-title");
      summary.getStyleClass().add("chapter-summary");
      details.getChildren().addAll(title, summary);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().add(details);
      row.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY && getItem() != null) activate(getItem());
          });
      row.setOnContextMenuRequested(
          event -> {
            if (getItem() != null) {
              showExerciseActions(getItem(), event.getSceneX(), event.getSceneY());
              event.consume();
            }
          });
    }

    @Override
    protected void updateItem(TacticExerciseSummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      title.setText(item.title());
      summary.setText(
          (item.readyToSolve() ? "Ready" : "Needs solution")
              + " · solve as "
              + colorName(item.solverColor()));
      setGraphic(row);
    }
  }
}
