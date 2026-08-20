package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.dto.AnalysisNotationNode;
import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.dto.PositionAnalysisResult;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.PositionAnalysisService;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.study.CreateChapterCommand;
import com.escontrela.lastmove.application.study.CreateChapterFromFenCommand;
import com.escontrela.lastmove.application.study.DeleteChapterCommand;
import com.escontrela.lastmove.application.study.ImportPgnChapterCommand;
import com.escontrela.lastmove.application.study.MoveChapterCommand;
import com.escontrela.lastmove.application.study.RenameChapterCommand;
import com.escontrela.lastmove.application.study.StudyChapterSummary;
import com.escontrela.lastmove.application.study.StudyChapterWorkspace;
import com.escontrela.lastmove.application.study.StudyDetails;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.notation.MoveNotationNode;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.service.ClipboardService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Dedicated board workspace for persisted study chapters. */
@Component
public final class StudyWorkspaceScreenController implements UiScreenController {

  private static final double BOARD_MAX_SIZE = 720.0;

  private static final StringConverter<ComputerEngineDescriptor> ENGINE_CONVERTER =
      new StringConverter<>() {
        @Override
        public String toString(ComputerEngineDescriptor descriptor) {
          return descriptor == null
              ? ""
              : descriptor.displayName() + " " + descriptor.version();
        }

        @Override
        public ComputerEngineDescriptor fromString(String value) {
          throw new UnsupportedOperationException("The analysis engine selector is not editable");
        }
      };

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MoveNotationControl moveNotation;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private TextInputModal textInputModal;
  @FXML private ListView<StudyChapterSummary> chapterList;
  @FXML private Label studyTitleLabel;
  @FXML private Label chapterTitleLabel;
  @FXML private Label statusLabel;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private CheckBox analysisEnabledCheckBox;
  @FXML private ComboBox<ComputerEngineDescriptor> analysisEngineCombo;
  @FXML private Label analysisBestMoveLabel;
  @FXML private Label analysisScoreLabel;

  private final StudyService studyService;
  private final CurrentUserService currentUserService;
  private final GameLoadService gameLoadService;
  private final FileChooserFactory fileChooserFactory;
  private final ChessSoundService chessSoundService;
  private final ClipboardService clipboardService;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final PositionAnalysisService positionAnalysisService;
  private final UiFlowManager uiFlowManager;
  private final PauseTransition analysisDebounce = new PauseTransition(Duration.millis(350));

  private StudyId activeStudyId;
  private StudyChapterId activeChapterId;
  private BoardMoveInput pendingPromotionMove;
  private List<StudyChapterSummary> visibleChapters = List.of();

  public StudyWorkspaceScreenController(
      StudyService studyService,
      CurrentUserService currentUserService,
      GameLoadService gameLoadService,
      FileChooserFactory fileChooserFactory,
      ChessSoundService chessSoundService,
      ClipboardService clipboardService,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      PositionAnalysisService positionAnalysisService,
      @Lazy UiFlowManager uiFlowManager) {
    this.studyService = studyService;
    this.currentUserService = currentUserService;
    this.gameLoadService = gameLoadService;
    this.fileChooserFactory = fileChooserFactory;
    this.chessSoundService = chessSoundService;
    this.clipboardService = clipboardService;
    this.boardAppearancePreferencesService = boardAppearancePreferencesService;
    this.positionAnalysisService = positionAnalysisService;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleNavigationShortcut);
    chessSoundService.preload();
    chessBoard.setSoundService(chessSoundService);
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    chapterList.setCellFactory(ignored -> new ChapterCell());
    configurePromotionPicker();
    configureNotation();
    configureEngineAnalysis();
    chessBoard.setOnPromotionRequested(
        event -> {
          pendingPromotionMove = event.getMoveInput();
          promotionPicker.showFor(event.getPromotingColor());
        });
    chessBoard.setOnMoveRequested(
        event -> attemptMove(event.getMoveInput()));
    bindResponsiveBoardSize();
  }

  @EventListener
  public void onOpenStudyWorkspace(OpenStudyWorkspaceEvent event) {
    activeStudyId = event.studyId();
    activeChapterId = event.chapterId();
  }

  @Override
  public void onShow() {
    if (activeStudyId == null || activeChapterId == null || activeOwner().isEmpty()) {
      uiFlowManager.show(UiScreenId.STUDIES);
      return;
    }
    refreshWorkspace();
  }

  @Override
  public void onHide() {
    analysisDebounce.stop();
    positionAnalysisService.close();
  }

  @FXML
  public void backToStudies() {
    uiFlowManager.show(UiScreenId.STUDIES);
  }

  @FXML
  public void onAddChapter() {
    textInputModal.setTitle("New chapter");
    textInputModal.setMessage("Create an empty chapter from the standard initial position.");
    textInputModal.setPromptText("Chapter title");
    textInputModal.setText("New chapter");
    textInputModal.setAcceptText("Create chapter");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnCancel(event -> statusLabel.setText("Chapter creation cancelled"));
    textInputModal.setOnAccept(
        event -> {
          String title = textInputModal.getText().trim();
          if (title.isEmpty()) {
            textInputModal.setValidationMessage("Enter a chapter title.");
            return;
          }
          activeOwner().ifPresent(
              owner -> {
                activeChapterId =
                    studyService
                        .createChapter(new CreateChapterCommand(owner, activeStudyId, title))
                        .chapterId();
                textInputModal.hide();
                refreshWorkspace();
                statusLabel.setText("Created chapter: " + title);
              });
        });
    textInputModal.show();
  }

  @FXML
  public void onAddChapterFromFen() {
    textInputModal.setTitle("Chapter from FEN");
    textInputModal.setMessage("Create a chapter from an exact chess position.");
    textInputModal.setPromptText("Paste a FEN position");
    textInputModal.setText("");
    textInputModal.setAcceptText("Create chapter");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnCancel(event -> statusLabel.setText("FEN chapter creation cancelled"));
    textInputModal.setOnAccept(
        event -> {
          try {
            Fen fen = Fen.of(textInputModal.getText().trim());
            activeOwner().ifPresent(
                owner -> {
                  activeChapterId =
                      studyService
                          .createChapterFromFen(
                              new CreateChapterFromFenCommand(
                                  owner, activeStudyId, "FEN chapter", fen))
                          .chapterId();
                  textInputModal.hide();
                  refreshWorkspace();
                  statusLabel.setText("Created chapter from FEN");
                });
          } catch (IllegalArgumentException exception) {
            textInputModal.setValidationMessage(messageOf(exception, "The FEN position is invalid."));
          }
        });
    textInputModal.show();
  }

  @FXML
  public void onImportPgnChapter() {
    fileChooserFactory
        .choosePgnFile(root.getScene().getWindow())
        .ifPresent(
            file -> {
              try {
                activeOwner().ifPresent(
                    owner -> {
                      activeChapterId =
                          studyService
                              .importPgnChapter(
                                  new ImportPgnChapterCommand(
                                      owner,
                                      activeStudyId,
                                      gameLoadService.importPgn(
                                          PgnImportRequest.fromFile(file.toPath()))))
                              .chapterId();
                      refreshWorkspace();
                      statusLabel.setText("Imported PGN as a chapter");
                    });
              } catch (RuntimeException exception) {
                statusLabel.setText(messageOf(exception, "Unable to import PGN."));
              }
            });
  }

  @FXML
  public void onFirstMove() {
    withOwner(owner -> chessBoard.renderPosition(studyService.first(owner, activeStudyId, activeChapterId)));
    refreshMoveList();
  }

  @FXML
  public void onPreviousMove() {
    withOwner(owner -> chessBoard.renderPosition(studyService.previous(owner, activeStudyId, activeChapterId)));
    refreshMoveList();
  }

  @FXML
  public void onNextMove() {
    withOwner(owner -> chessBoard.renderPosition(studyService.next(owner, activeStudyId, activeChapterId)));
    refreshMoveList();
  }

  @FXML
  public void onLastMove() {
    withOwner(owner -> chessBoard.renderPosition(studyService.last(owner, activeStudyId, activeChapterId)));
    refreshMoveList();
  }

  @FXML
  public void onCopyFen() {
    activeOwner().ifPresent(
        owner -> {
          boolean copied = clipboardService.copyText(studyService.currentFen(owner, activeStudyId, activeChapterId));
          statusLabel.setText(copied ? "FEN copied to clipboard" : "Unable to copy FEN to clipboard");
        });
  }

  @FXML
  public void onRotateBoard() {
    boolean flipped = chessBoard.toggleOrientation();
    statusLabel.setText(flipped ? "Board rotated: Black at bottom" : "Board rotated: White at bottom");
  }

  private void attemptMove(BoardMoveInput moveInput) {
    activeOwner().ifPresent(
        owner -> {
          MoveExecutionResult result =
              studyService.attemptMove(
                  owner,
                  activeStudyId,
                  activeChapterId,
                  new MoveCommand(
                      moveInput.fromSquare(), moveInput.toSquare(), moveInput.promotionPiece()));
          if (result.accepted()) {
            chessBoard.renderPosition(result.newSnapshot());
            refreshMoveList();
          } else {
            statusLabel.setText(result.rejectionReason().orElse("Move is not legal."));
          }
        });
  }

  private void configurePromotionPicker() {
    promotionPicker.setOnPromotionSelected(
        event -> {
          if (pendingPromotionMove != null) {
            BoardMoveInput completeMove = pendingPromotionMove.withPromotion(event.pieceType());
            pendingPromotionMove = null;
            chessBoard.handleBoardMoveInput(completeMove);
          }
        });
    promotionPicker.setOnCancel(
        event -> {
          pendingPromotionMove = null;
          statusLabel.setText("Promotion cancelled");
        });
  }

  private void configureNotation() {
    moveNotation.setOnPlySelected(
        event -> {
          activeOwner().ifPresent(
              owner -> {
                chessBoard.renderPosition(
                    studyService.select(
                        owner,
                        activeStudyId,
                        activeChapterId,
                        new AnalysisNodeId(event.getEntry().nodeId())));
                refreshMoveList();
              });
        });
  }

  private void configureEngineAnalysis() {
    analysisEngineCombo.setConverter(ENGINE_CONVERTER);
    analysisEngineCombo.setItems(
        FXCollections.observableArrayList(positionAnalysisService.availableEngines()));
    selectAnalysisEngine(positionAnalysisService.defaultEngineId());
    analysisEnabledCheckBox.setSelected(true);
    analysisDebounce.setOnFinished(event -> requestAnalysis());

    chessBoard
        .positionProperty()
        .addListener(
            (observable, oldPosition, newPosition) -> {
              if (newPosition != null) {
                analysisDebounce.playFromStart();
              }
            });

    analysisEnabledCheckBox
        .selectedProperty()
        .addListener(
            (observable, oldValue, enabled) -> {
              if (enabled) {
                requestAnalysis();
              } else {
                clearAnalysisLabels();
              }
            });

    analysisEngineCombo
        .valueProperty()
        .addListener(
            (observable, oldValue, selectedEngine) -> {
              if (selectedEngine != null && analysisEnabledCheckBox.isSelected()) {
                requestAnalysis();
              }
            });
  }

  private void selectAnalysisEngine(String engineId) {
    for (ComputerEngineDescriptor engine : analysisEngineCombo.getItems()) {
      if (engine.id().equals(engineId)) {
        analysisEngineCombo.getSelectionModel().select(engine);
        return;
      }
    }
    analysisEngineCombo.getSelectionModel().selectFirst();
  }

  private void requestAnalysis() {
    if (!analysisEnabledCheckBox.isSelected()) {
      clearAnalysisLabels();
      return;
    }
    ComputerEngineDescriptor selectedEngine = analysisEngineCombo.getValue();
    PositionSnapshot position = chessBoard.getPosition();
    if (selectedEngine == null || position == null) {
      clearAnalysisLabels();
      return;
    }
    analysisBestMoveLabel.setText("Best move: …");
    analysisScoreLabel.setText("Analysing with " + selectedEngine.displayName() + "…");
    positionAnalysisService
        .analyze(position, selectedEngine.id())
        .whenComplete(
            (result, failure) ->
                Platform.runLater(
                    () -> {
                      if (failure != null) {
                        showAnalysisFailure(failure);
                      } else if (result != null) {
                        result.ifPresent(this::renderAnalysis);
                      }
                    }));
  }

  private void renderAnalysis(PositionAnalysisResult result) {
    analysisBestMoveLabel.setText("Best move: " + result.bestMoveSan().orElse("—"));
    String score = result.scoreText().orElse("—");
    String depth = result.depth().map(value -> "  ·  depth " + value).orElse("");
    analysisScoreLabel.setText("Score: " + score + depth);
  }

  private void showAnalysisFailure(Throwable failure) {
    analysisBestMoveLabel.setText("Best move: —");
    analysisScoreLabel.setText("Analysis unavailable");
  }

  private void clearAnalysisLabels() {
    analysisBestMoveLabel.setText("Best move: —");
    analysisScoreLabel.setText("Score: —");
  }

  private void refreshWorkspace() {
    PlayerId owner = activeOwner().orElse(null);
    if (owner == null) {
      return;
    }
    StudyDetails study = studyService.studyDetails(owner, activeStudyId);
    visibleChapters = study.chapters();
    chapterList.getItems().setAll(visibleChapters);
    if (visibleChapters.stream().noneMatch(chapter -> chapter.chapterId().equals(activeChapterId))) {
      activeChapterId = visibleChapters.getFirst().chapterId();
    }
    StudyChapterWorkspace workspace = studyService.openChapter(owner, activeStudyId, activeChapterId);
    studyTitleLabel.setText(study.study().title());
    chapterTitleLabel.setText(workspace.title());
    chessBoard.renderPosition(workspace.currentPosition());
    refreshMoveList();
    chapterList.refresh();
    statusLabel.setText("Editing " + workspace.title());
  }

  private void refreshMoveList() {
    PlayerId owner = activeOwner().orElse(null);
    if (owner == null || activeStudyId == null || activeChapterId == null) {
      return;
    }
    AnalysisNotationTree tree = studyService.notationTree(owner, activeStudyId, activeChapterId);
    moveNotation.setTree(tree.roots().stream().map(this::toNotationNode).toList());
    moveNotation.setSelectedNodeId(tree.currentNodeId().map(id -> id.value()).orElse(null));
  }

  private MoveNotationNode toNotationNode(AnalysisNotationNode node) {
    var ply = node.ply();
    return new MoveNotationNode(
        new MoveNotationEntry(
            node.nodeId().value(),
            ply.moveNumber(),
            ply.movingColor() == PieceColor.WHITE,
            ply.move().san().getValue(),
            node.activeLine()),
        node.continuations().stream().map(this::toNotationNode).toList());
  }

  private void activateChapter(StudyChapterSummary chapter) {
    activeChapterId = chapter.chapterId();
    refreshWorkspace();
  }

  private void renameChapter(StudyChapterSummary chapter) {
    textInputModal.setTitle("Rename chapter");
    textInputModal.setMessage("Choose a clear title for this chapter.");
    textInputModal.setPromptText("Chapter title");
    textInputModal.setText(chapter.title());
    textInputModal.setAcceptText("Rename");
    textInputModal.setCancelText("Cancel");
    textInputModal.setOnAccept(
        event -> {
          String title = textInputModal.getText().trim();
          if (title.isEmpty()) {
            textInputModal.setValidationMessage("Enter a chapter title.");
            return;
          }
          activeOwner().ifPresent(
              owner -> {
                studyService.renameChapter(
                    new RenameChapterCommand(owner, activeStudyId, chapter.chapterId(), title));
                textInputModal.hide();
                refreshWorkspace();
              });
        });
    textInputModal.show();
  }

  private void moveChapter(StudyChapterSummary chapter, int offset) {
    activeOwner().ifPresent(
        owner -> {
          studyService.moveChapter(
              new MoveChapterCommand(owner, activeStudyId, chapter.chapterId(), offset));
          refreshWorkspace();
        });
  }

  private void deleteChapter(StudyChapterSummary chapter) {
    activeOwner().ifPresent(
        owner -> {
          studyService.deleteChapter(
              new DeleteChapterCommand(owner, activeStudyId, chapter.chapterId()));
          StudyDetails details = studyService.studyDetails(owner, activeStudyId);
          if (details.chapters().isEmpty()) {
            activeChapterId =
                studyService
                    .createChapter(new CreateChapterCommand(owner, activeStudyId, "Chapter 1"))
                    .chapterId();
          } else if (chapter.chapterId().equals(activeChapterId)) {
            activeChapterId = details.chapters().getFirst().chapterId();
          }
          refreshWorkspace();
          statusLabel.setText("Deleted chapter: " + chapter.title());
        });
  }

  private Optional<PlayerId> activeOwner() {
    return currentUserService.activePlayerState().playerId();
  }

  private void withOwner(java.util.function.Consumer<PlayerId> action) {
    activeOwner().ifPresent(action);
  }

  private void handleNavigationShortcut(KeyEvent event) {
    if (event.isConsumed()
        || textInputModal.isVisible()
        || promotionPicker.isVisible()
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

  private void bindResponsiveBoardSize() {
    ChangeListener<Number> listener = (ignored, oldValue, newValue) -> updateBoardSize();
    boardHost.widthProperty().addListener(listener);
    boardHost.heightProperty().addListener(listener);
    updateBoardSize();
  }

  private void updateBoardSize() {
    double available = Math.min(boardHost.getWidth(), boardHost.getHeight());
    if (available > 0) {
      double side = Math.min(available, BOARD_MAX_SIZE);
      chessBoard.setPrefSize(side, side);
    }
  }

  private static String messageOf(RuntimeException exception, String fallback) {
    return Objects.toString(exception.getMessage(), "").isBlank() ? fallback : exception.getMessage();
  }

  private void showChapterActions(StudyChapterSummary chapter, double sceneX, double sceneY) {
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Rename chapter…", "", event -> renameChapter(chapter));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Move chapter up", "↑", event -> moveChapter(chapter, -1));
    contextualMenuPanel.addItem("Move chapter down", "↓", event -> moveChapter(chapter, 1));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete chapter…", "", event -> deleteChapter(chapter));
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private final class ChapterCell extends ListCell<StudyChapterSummary> {

    private final HBox row = new HBox(8);
    private final VBox details = new VBox(3);
    private final Label activeMarker = new Label("✓");
    private final Label title = new Label();
    private final Label summary = new Label();

    private ChapterCell() {
      row.getStyleClass().add("chapter-row");
      row.setAlignment(Pos.CENTER_LEFT);
      activeMarker.getStyleClass().add("session-active-marker");
      title.getStyleClass().add("chapter-title");
      summary.getStyleClass().add("chapter-summary");
      details.getChildren().addAll(title, summary);
      details.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().addAll(activeMarker, details);
      row.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY && getItem() != null) {
              activateChapter(getItem());
            }
          });
      row.setOnContextMenuRequested(
          event -> {
            if (getItem() != null) {
              showChapterActions(getItem(), event.getSceneX(), event.getSceneY());
              event.consume();
            }
          });
    }

    @Override
    protected void updateItem(StudyChapterSummary item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        return;
      }
      row.getStyleClass().remove("chapter-row-active");
      boolean active = item.chapterId().equals(activeChapterId);
      if (active) {
        row.getStyleClass().add("chapter-row-active");
      }
      activeMarker.setVisible(active);
      activeMarker.setManaged(active);
      title.setText(item.title());
      summary.setText(item.origin().name().replace('_', ' ') + " · " + item.moveCount() + " moves");
      setGraphic(row);
    }
  }
}
