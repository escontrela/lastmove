package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.dto.AnalysisNotationNode;
import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.application.dto.PgnImportRequest;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.EngineEvaluationService;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.service.StudyAnnotationService;
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
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.comment.CommentPanel;
import com.escontrela.lastmove.ui.component.comment.SpeakerNotesPanel;
import com.escontrela.lastmove.ui.component.evaluation.EngineEvaluationControl;
import com.escontrela.lastmove.ui.component.evaluation.EngineSelectorModal;
import com.escontrela.lastmove.ui.component.message.TextInputModal;
import com.escontrela.lastmove.ui.component.message.MultilineTextInputModal;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.component.tree.MoveTreeOverlay;
import com.escontrela.lastmove.ui.component.notation.MoveNotationControl;
import com.escontrela.lastmove.ui.component.notation.MoveNotationEntry;
import com.escontrela.lastmove.ui.component.notation.MoveNotationNode;
import com.escontrela.lastmove.ui.component.promotion.PromotionPickerControl;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.event.OpenStudyChapterTacticEvent;
import com.escontrela.lastmove.ui.event.OpenChapterPositionEditorEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import com.escontrela.lastmove.ui.service.ClipboardService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Dedicated board workspace for persisted study chapters. */
@Component
public final class StudyWorkspaceScreenController implements UiScreenController {

  private static final double BOARD_MAX_SIZE = 720.0;
  private static final String EMPTY_COMMENT_LIGHT_ICON = "/images/mode_comment_35dp_000000.png";
  private static final String EMPTY_COMMENT_DARK_ICON = "/images/mode_comment_35dp_FFFFFF.png";
  private static final String COMMENT_LIGHT_ICON = "/images/comment_35dp_000000.png";
  private static final String COMMENT_DARK_ICON = "/images/comment_35dp_FFFFFF.png";

  @FXML private StackPane root;
  @FXML private StackPane boardHost;
  @FXML private ChessBoardControl chessBoard;
  @FXML private MoveNotationControl moveNotation;
  @FXML private PromotionPickerControl promotionPicker;
  @FXML private TextInputModal textInputModal;
  @FXML private ListView<StudyChapterSummary> chapterList;
  @FXML private Label studyTitleLabel;
  @FXML private Label studySourceTitleLabel;
  @FXML private Label chapterTitleLabel;
  @FXML private Label statusLabel;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private EngineEvaluationControl engineEvaluation;
  @FXML private EngineSelectorModal engineSelectorModal;
  @FXML private CommentPanel commentPanel;
  @FXML private SpeakerNotesPanel speakerNotesPanel;
  @FXML private MultilineTextInputModal commentEditor;
  @FXML private MessageBox moveDeleteConfirmation;
  @FXML private ToolbarIconButton studyCommentButton;
  @FXML private ToolbarIconButton moveCommentButton;
  @FXML private ToolbarIconButton speakerNotesButton;
  @FXML private MoveTreeOverlay moveTreeOverlay;

  private final StudyService studyService;
  private final StudyAnnotationService annotationService;
  private final CurrentUserService currentUserService;
  private final GameLoadService gameLoadService;
  private final FileChooserFactory fileChooserFactory;
  private final ChessSoundService chessSoundService;
  private final ClipboardService clipboardService;
  private final BoardAppearancePreferencesService boardAppearancePreferencesService;
  private final EngineEvaluationService engineEvaluationService;
  private final UiFlowManager uiFlowManager;
  private final UiEventBus uiEventBus;
  private Runnable removeEvaluationSubscription = () -> {};

  private StudyId activeStudyId;
  private StudyChapterId activeChapterId;
  private BoardMoveInput pendingPromotionMove;
  private List<StudyChapterSummary> visibleChapters = List.of();
  private AnalysisNodeId currentNodeId;
  private Runnable editCurrentComment = () -> {};

  public StudyWorkspaceScreenController(
      StudyService studyService,
      StudyAnnotationService annotationService,
      CurrentUserService currentUserService,
      GameLoadService gameLoadService,
      FileChooserFactory fileChooserFactory,
      ChessSoundService chessSoundService,
      ClipboardService clipboardService,
      BoardAppearancePreferencesService boardAppearancePreferencesService,
      EngineEvaluationService engineEvaluationService,
      UiEventBus uiEventBus,
      @Lazy UiFlowManager uiFlowManager) {
    this.studyService = studyService;
    this.annotationService = annotationService;
    this.currentUserService = currentUserService;
    this.gameLoadService = gameLoadService;
    this.fileChooserFactory = fileChooserFactory;
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
    chessBoard.setSoundService(chessSoundService);
    chessBoard.visualEffectsEnabledProperty().bind(
        boardAppearancePreferencesService.boardVisualEffectsEnabledProperty());
    chessBoard.appearancePresetProperty().bind(
        boardAppearancePreferencesService.boardAppearancePresetProperty());
    moveTreeOverlay.bindBoardAppearance(
        boardAppearancePreferencesService.boardAppearancePresetProperty());
    chapterList.setCellFactory(ignored -> new ChapterCell());
    configurePromotionPicker();
    configureNotation();
    configureSpeakerNotes();
    configureMoveTreeOverlay();
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
    removeEvaluationSubscription.run();
    engineEvaluationService.cancel();
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

  /** Creates a provisional chapter and lets the author compose its initial position. */
  @FXML
  public void onAddChapterPosition() {
    activeOwner().ifPresent(
        owner -> {
          var chapter =
              studyService.createChapter(
                  new CreateChapterCommand(owner, activeStudyId, "Unknown chapter"));
          activeChapterId = chapter.chapterId();
          uiEventBus.publish(
              new OpenChapterPositionEditorEvent(
                  studyService.chapterPositionEditContext(
                      owner, activeStudyId, chapter.chapterId())));
          uiFlowManager.show(UiScreenId.POSITION_EDITOR);
        });
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

  @FXML
  public void onStudyComment() {
    activeOwner().ifPresent(owner -> showComment("Study comment",
        annotationService.studyComment(owner, activeStudyId).orElse(""),
        text -> annotationService.saveStudyComment(owner, activeStudyId, text),
        this::refreshCommentIcons));
  }

  @FXML
  public void onMoveComment() {
    if (currentNodeId == null) return;
    AnalysisNodeId annotatedNodeId = currentNodeId;
    activeOwner().ifPresent(owner -> {
      String san = selectedSan().orElse("move");
      showComment("Move comment · " + san,
          annotationService.moveComment(owner, activeStudyId, activeChapterId, annotatedNodeId).orElse(""),
          text -> annotationService.saveMoveComment(owner, activeStudyId, activeChapterId, annotatedNodeId, text),
          this::refreshCommentIcons);
    });
  }

  @FXML
  public void onSpeakerNotes() {
    activeOwner().ifPresent(
        owner -> {
          StudyChapterWorkspace workspace = studyService.openChapter(owner, activeStudyId, activeChapterId);
          Map<AnalysisNodeId, String> moveComments =
              annotationService.moveComments(owner, activeStudyId, activeChapterId);
          speakerNotesPanel.showNotes(
              annotationService.studyComment(owner, activeStudyId).orElse(""),
              workspace.title(),
              annotationService.chapterComment(owner, activeStudyId, activeChapterId).orElse(""),
              speakerTreeNotes(workspace.notationTree().roots(), moveComments),
              speakerStoryLines(workspace.notationTree().roots(), moveComments));
          speakerNotesPanel.show();
        });
  }

  private List<SpeakerNotesPanel.MoveNote> speakerTreeNotes(
      List<AnalysisNotationNode> nodes, Map<AnalysisNodeId, String> moveComments) {
    List<SpeakerNotesPanel.MoveNote> notes = new ArrayList<>();
    appendSpeakerNotes(nodes, moveComments, "", notes);
    return List.copyOf(notes);
  }

  private List<SpeakerNotesPanel.StoryLine> speakerStoryLines(
      List<AnalysisNotationNode> nodes, Map<AnalysisNodeId, String> moveComments) {
    List<SpeakerNotesPanel.StoryLine> lines = new ArrayList<>();
    appendSpeakerStoryLines(nodes, moveComments, List.of(), lines);
    return List.copyOf(lines);
  }

  private void appendSpeakerStoryLines(
      List<AnalysisNotationNode> nodes,
      Map<AnalysisNodeId, String> moveComments,
      List<SpeakerNotesPanel.MoveNote> precedingMoves,
      List<SpeakerNotesPanel.StoryLine> lines) {
    for (AnalysisNotationNode node : nodes) {
      List<SpeakerNotesPanel.MoveNote> line = new ArrayList<>(precedingMoves);
      line.add(
          new SpeakerNotesPanel.MoveNote(
              node.nodeId().value(), moveReference(node), moveComments.getOrDefault(node.nodeId(), ""), ""));
      if (node.continuations().isEmpty()) {
        if (line.stream().anyMatch(note -> !note.comment().isBlank())) {
          lines.add(new SpeakerNotesPanel.StoryLine(line));
        }
      } else {
        appendSpeakerStoryLines(node.continuations(), moveComments, line, lines);
      }
    }
  }

  private void appendSpeakerNotes(
      List<AnalysisNotationNode> nodes,
      Map<AnalysisNodeId, String> moveComments,
      String ancestorPrefix,
      List<SpeakerNotesPanel.MoveNote> notes) {
    for (int index = 0; index < nodes.size(); index++) {
      AnalysisNotationNode node = nodes.get(index);
      boolean last = index == nodes.size() - 1;
      boolean variation = index > 0;
      String treePrefix =
          variation ? ancestorPrefix + (last ? "└─ " : "├─ ") : ancestorPrefix;
      String comment = moveComments.getOrDefault(node.nodeId(), "");
      if (!comment.isBlank()) {
        notes.add(
            new SpeakerNotesPanel.MoveNote(
                node.nodeId().value(), moveReference(node), comment, treePrefix));
      }
      appendSpeakerNotes(
          node.continuations(),
          moveComments,
          variation ? ancestorPrefix + (last ? "   " : "│  ") : ancestorPrefix,
          notes);
    }
  }

  private static String moveReference(AnalysisNotationNode node) {
    boolean whiteMove = node.ply().movingColor() == PieceColor.WHITE;
    String turn = node.ply().moveNumber() + (whiteMove ? "." : "...");
    return turn + " " + (whiteMove ? "White" : "Black") + " · " + node.ply().move().san().getValue();
  }

  private MoveTreeOverlay.TreeNode toMoveTreeNode(
      AnalysisNotationNode node, Map<AnalysisNodeId, String> comments) {
    return new MoveTreeOverlay.TreeNode(
        node.nodeId().value(),
        treeMoveReference(node),
        node.mainContinuation(),
        node.current(),
        comments.getOrDefault(node.nodeId(), ""),
        node.ply().resultingPosition(),
        node.continuations().stream().map(child -> toMoveTreeNode(child, comments)).toList());
  }

  private static String treeMoveReference(AnalysisNotationNode node) {
    boolean whiteMove = node.ply().movingColor() == PieceColor.WHITE;
    return node.ply().moveNumber()
        + (whiteMove ? ". " : "... ")
        + node.ply().move().san().getValue();
  }

  private void showChapterComment(StudyChapterSummary chapter) {
    activeOwner().ifPresent(owner -> showComment("Chapter comment · " + chapter.title(),
        annotationService.chapterComment(owner, activeStudyId, chapter.chapterId()).orElse(""),
        text -> annotationService.saveChapterComment(owner, activeStudyId, chapter.chapterId(), text),
        this::refreshCommentIcons));
  }

  private void showComment(
      String title,
      String value,
      java.util.function.Consumer<String> saveAction,
      Runnable afterSave) {
    commentPanel.setTitle(title); commentPanel.setContent(value);
    editCurrentComment = () -> commentEditor.show(title, commentPanel.getContent(), event -> {
      saveAction.accept(commentEditor.getText());
      commentPanel.setContent(commentEditor.getText().strip());
      commentEditor.hide();
      afterSave.run();
      statusLabel.setText(commentPanel.getContent().isBlank() ? "Comment removed" : "Comment saved");
    });
    commentPanel.setOnEdit(event -> editCurrentComment.run());
    commentPanel.show();
  }

  private Optional<String> selectedSan() {
    if (currentNodeId == null) return Optional.empty();
    return findSan(moveNotation.getTree(), currentNodeId.value());
  }

  private Optional<String> findSan(List<MoveNotationNode> nodes, java.util.UUID id) {
    for (MoveNotationNode node : nodes) {
      if (node.entry().nodeId().equals(id)) return Optional.of(node.entry().san());
      Optional<String> nested = findSan(node.continuations(), id);
      if (nested.isPresent()) return nested;
    }
    return Optional.empty();
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
    moveNotation.setOnPlyContextRequested(this::showMoveContextMenu);
  }

  private void configureSpeakerNotes() {
    speakerNotesPanel.setOnMoveNoteSelected(
        event ->
            activeOwner()
                .ifPresent(
                    owner -> {
                      AnalysisNodeId nodeId = new AnalysisNodeId(event.getNote().nodeId());
                      chessBoard.renderPosition(
                          studyService.select(owner, activeStudyId, activeChapterId, nodeId));
                      refreshMoveList();
                      statusLabel.setText("Selected " + event.getNote().moveReference());
                    }));
  }

  private void configureMoveTreeOverlay() {
    moveTreeOverlay.setOnNodeConfirmed(
        event ->
            activeOwner()
                .ifPresent(
                    owner -> {
                      chessBoard.renderPosition(
                          studyService.select(
                              owner,
                              activeStudyId,
                              activeChapterId,
                              new AnalysisNodeId(event.getNode().nodeId())));
                      moveTreeOverlay.hide();
                      refreshMoveList();
                      statusLabel.setText("Selected " + event.getNode().moveReference());
                    }));
  }

  private void showMoveContextMenu(MoveNotationControl.PlyContextRequestedEvent event) {
    MoveNotationNode node = findNotationNode(moveNotation.getTree(), event.getEntry().nodeId());
    if (node == null) {
      refreshMoveList();
      return;
    }
    int branchSize = branchSize(node);
    String action = branchSize == 1 ? "Delete move" : "Delete variation";
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Visualize tree", "", ignored -> showMoveTree());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem(
        action,
        "",
        ignored -> confirmMoveDeletion(event.getEntry(), branchSize));
    contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
  }

  private void showMoveTree() {
    activeOwner().ifPresent(
        owner -> {
          AnalysisNotationTree tree = studyService.notationTree(owner, activeStudyId, activeChapterId);
          Map<AnalysisNodeId, String> comments =
              annotationService.moveComments(owner, activeStudyId, activeChapterId);
          moveTreeOverlay.setTree(
              tree.roots().stream().map(node -> toMoveTreeNode(node, comments)).toList());
          moveTreeOverlay.show();
        });
  }

  private void confirmMoveDeletion(MoveNotationEntry entry, int branchSize) {
    boolean variation = branchSize > 1;
    moveDeleteConfirmation.setTitle(variation ? "Delete variation?" : "Delete move?");
    moveDeleteConfirmation.setMessage(
        variation
            ? "Delete " + entry.san() + " and its " + (branchSize - 1) + " continuation moves? This cannot be undone."
            : "Delete " + entry.san() + "? This cannot be undone.");
    moveDeleteConfirmation.setAcceptText(variation ? "Delete variation" : "Delete move");
    moveDeleteConfirmation.setCancelText("Cancel");
    moveDeleteConfirmation.setOnAccept(
        ignored ->
            activeOwner()
                .ifPresent(
                    owner -> {
                      boolean refreshOpenNotes = speakerNotesPanel.isVisible();
                      studyService.deleteBranch(
                          owner, activeStudyId, activeChapterId, new AnalysisNodeId(entry.nodeId()));
                      chessBoard.renderPosition(
                          studyService.currentPosition(owner, activeStudyId, activeChapterId));
                      commentPanel.hide();
                      refreshMoveList();
                      if (refreshOpenNotes) {
                        onSpeakerNotes();
                      }
                      statusLabel.setText(variation ? "Variation deleted" : "Move deleted");
                    }));
    moveDeleteConfirmation.setOnCancel(
        ignored -> statusLabel.setText(variation ? "Variation kept" : "Move kept"));
    moveDeleteConfirmation.show();
  }

  private static MoveNotationNode findNotationNode(List<MoveNotationNode> nodes, java.util.UUID id) {
    for (MoveNotationNode node : nodes) {
      if (node.entry().nodeId().equals(id)) return node;
      MoveNotationNode child = findNotationNode(node.continuations(), id);
      if (child != null) return child;
    }
    return null;
  }

  private static int branchSize(MoveNotationNode node) {
    return 1
        + node.continuations().stream()
            .mapToInt(StudyWorkspaceScreenController::branchSize)
            .sum();
  }

  private void configureEngineAnalysis() {
    removeEvaluationSubscription = engineEvaluationService.subscribe(
        state -> Platform.runLater(() -> engineEvaluation.render(state)));
    engineEvaluation.setOnChangeEngine(
        event -> engineSelectorModal.show(
            engineEvaluationService.availableEngines(), engineEvaluationService.state().engine().id()));
    engineSelectorModal.setOnEngineSelected(
        event -> engineEvaluationService.selectEngine(event.engineId()));
    chessBoard
        .positionProperty()
        .addListener(
            (observable, oldPosition, newPosition) -> {
              if (newPosition != null) {
                engineEvaluationService.analyze(newPosition);
              }
            });

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
    studySourceTitleLabel.setText(study.study().title());
    chapterTitleLabel.setText(workspace.title());
    chessBoard.renderPosition(workspace.currentPosition());
    refreshMoveList();
    chapterList.refresh();
    refreshCommentIcons();
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
    currentNodeId = tree.currentNodeId().orElse(null);
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
            ply.movingColor() == PieceColor.WHITE,
            ply.move().san().getValue(),
            node.activeLine()),
        node.continuations().stream().map(this::toNotationNode).toList());
  }

  private void refreshCommentIcons() {
    refreshStudyCommentIcon();
    refreshMoveCommentIcon();
    chapterList.refresh();
  }

  private void refreshStudyCommentIcon() {
    boolean hasComment =
        activeStudyId != null
            && activeOwner()
                .map(owner -> annotationService.studyComment(owner, activeStudyId).isPresent())
                .orElse(false);
    setCommentIcon(studyCommentButton, hasComment);
  }

  private void refreshMoveCommentIcon() {
    boolean hasComment =
        currentNodeId != null
            && activeStudyId != null
            && activeChapterId != null
            && activeOwner()
                .map(
                    owner ->
                        annotationService
                            .moveComment(owner, activeStudyId, activeChapterId, currentNodeId)
                            .isPresent())
                .orElse(false);
    setCommentIcon(moveCommentButton, hasComment);
  }

  private static void setCommentIcon(ToolbarIconButton button, boolean hasComment) {
    button.setLightIconResource(hasComment ? COMMENT_LIGHT_ICON : EMPTY_COMMENT_LIGHT_ICON);
    button.setDarkIconResource(hasComment ? COMMENT_DARK_ICON : EMPTY_COMMENT_DARK_ICON);
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
    contextualMenuPanel.addItem(
        "Edit chapter position…", "", event -> editChapterPosition(chapter));
    contextualMenuPanel.addItem("Rename chapter…", "", event -> renameChapter(chapter));
    contextualMenuPanel.addItem("Comments / Edit comment…", "", event -> showChapterComment(chapter));
    contextualMenuPanel.addItem("Visualize tree", "", event -> showChapterTree(chapter));
    contextualMenuPanel.addItem("Run chapter as tactic", "", event -> runChapterAsTactic(chapter));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Move chapter up", "↑", event -> moveChapter(chapter, -1));
    contextualMenuPanel.addItem("Move chapter down", "↓", event -> moveChapter(chapter, 1));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete chapter…", "", event -> deleteChapter(chapter));
    contextualMenuPanel.showAtScene(sceneX, sceneY);
  }

  private void editChapterPosition(StudyChapterSummary chapter) {
    activeOwner().ifPresent(
        owner -> {
          uiEventBus.publish(
              new OpenChapterPositionEditorEvent(
                  studyService.chapterPositionEditContext(
                      owner, activeStudyId, chapter.chapterId())));
          uiFlowManager.show(UiScreenId.POSITION_EDITOR);
        });
  }

  private void runChapterAsTactic(StudyChapterSummary chapter) {
    if (activeStudyId == null) return;
    uiEventBus.publish(new OpenStudyChapterTacticEvent(activeStudyId, chapter.chapterId()));
    uiFlowManager.show(UiScreenId.TACTICS_WORKSPACE);
  }

  private void showChapterTree(StudyChapterSummary chapter) {
    if (!chapter.chapterId().equals(activeChapterId)) {
      activeChapterId = chapter.chapterId();
      refreshWorkspace();
    }
    showMoveTree();
  }

  private final class ChapterCell extends ListCell<StudyChapterSummary> {

    private final HBox row = new HBox(8);
    private final VBox details = new VBox(3);
    private final Label activeMarker = new Label("✓");
    private final Label title = new Label();
    private final Label summary = new Label();
    private final ToolbarIconButton commentButton = new ToolbarIconButton();

    private ChapterCell() {
      row.getStyleClass().add("chapter-row");
      row.setAlignment(Pos.CENTER_LEFT);
      activeMarker.getStyleClass().add("session-active-marker");
      title.getStyleClass().add("chapter-title");
      title.setWrapText(true);
      title.setMaxWidth(Double.MAX_VALUE);
      summary.getStyleClass().add("chapter-summary");
      commentButton.getStyleClass().add("chapter-comment-button");
      commentButton.setAccessibleText("Chapter comments");
      commentButton.setTooltipText("Chapter comments");
      commentButton.setOnAction(event -> showChapterComment(getItem()));
      commentButton.setOnMouseClicked(event -> event.consume());
      details.getChildren().addAll(title, summary);
      details.setMinWidth(0);
      details.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().addAll(activeMarker, details, commentButton);
      row.setMaxWidth(Double.MAX_VALUE);
      row.prefWidthProperty().bind(widthProperty().subtract(2));
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
      boolean hasComment =
          activeStudyId != null
              && activeOwner()
                  .map(owner -> annotationService.chapterComment(owner, activeStudyId, item.chapterId()).isPresent())
                  .orElse(false);
      setCommentIcon(commentButton, hasComment);
      setGraphic(row);
    }
  }
}
