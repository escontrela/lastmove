package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.PositionEditorService;
import com.escontrela.lastmove.application.service.PositionEditorService.PositionEditorState;
import com.escontrela.lastmove.application.service.StudyService;
import com.escontrela.lastmove.application.study.EditChapterInitialPositionCommand;
import com.escontrela.lastmove.application.study.StudyChapterPositionEditContext;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.board.BoardPieceDragPayload;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.OpenChapterPositionEditorEvent;
import com.escontrela.lastmove.ui.event.OpenStudyWorkspaceEvent;
import com.escontrela.lastmove.ui.event.SelectStudyDestinationEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ClipboardService;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.control.RadioButton;
import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** JavaFX adapter for the one transient position-authoring session. */
@Component
public final class PositionEditorScreenController implements UiScreenController {
  @FXML private StackPane root;
  @FXML private ChessBoardControl chessBoard;
  @FXML private Label statusLabel;
  @FXML private Label selectedPieceLabel;
  @FXML private TextArea fenText;
  @FXML private RadioButton whiteToMove;
  @FXML private RadioButton blackToMove;
  @FXML private CheckBox whiteKingSide, whiteQueenSide, blackKingSide, blackQueenSide;
  @FXML private ComboBox<String> enPassantCombo;
  @FXML private Spinner<Integer> halfmoveSpinner, fullmoveSpinner;
  @FXML private Node openAnalysisButton;
  @FXML private Node addStudyButton;
  @FXML private ToolbarIconButton saveChapterPositionButton;
  @FXML private MessageBox discardMovesConfirmation;
  private final PositionEditorService editor;
  private final StudyService studyService;
  private final AnalysisSessionService analysisSessions;
  private final CurrentUserService currentUserService;
  private final ClipboardService clipboard;
  private final UiEventBus events;
  private final UiFlowManager flow;
  private PieceType selectedType;
  private PieceColor selectedColor;
  private StudyChapterPositionEditContext chapterEditContext;

  public PositionEditorScreenController(PositionEditorService editor, StudyService studyService, AnalysisSessionService analysisSessions,
      CurrentUserService currentUserService, ClipboardService clipboard, UiEventBus events, @Lazy UiFlowManager flow) {
    this.editor = editor; this.studyService = studyService; this.analysisSessions = analysisSessions; this.currentUserService = currentUserService;
    this.clipboard = clipboard; this.events = events; this.flow = flow;
  }
  @FXML public void initialize() {
    root.getProperties().put("controller", this);
    chessBoard.setEditorMode(true);
    chessBoard.setOnMoveRequested(event -> { editor.move(event.getMoveInput().fromSquare(), event.getMoveInput().toSquare()); refresh(); });
    chessBoard.setOnPieceRemovalRequested(event -> { editor.remove(event.getSquare()); refresh(); });
    chessBoard.setOnEditorSquareRequested(event -> { if (selectedType != null) { editor.place(event.getSquare(), selectedType, selectedColor); refresh(); } });
    chessBoard.setOnPieceDropped(event -> {
      editor.place(event.getSquare(), event.getPieceType(), event.getPieceColor());
      refresh();
    });
    ToggleGroup side = new ToggleGroup(); whiteToMove.setToggleGroup(side); blackToMove.setToggleGroup(side);
    refresh();
  }
  @EventListener
  public void onOpenChapterPositionEditor(OpenChapterPositionEditorEvent event) {
    chapterEditContext = event.context();
  }

  @Override public void onShow() {
    boolean editingChapter = chapterEditContext != null;
    if (editingChapter) {
      editor.load(chapterEditContext.initialPosition());
      statusLabel.setText("Editing the initial position of this study chapter");
    }
    openAnalysisButton.setDisable(editingChapter);
    addStudyButton.setDisable(editingChapter);
    saveChapterPositionButton.setManaged(editingChapter);
    saveChapterPositionButton.setVisible(editingChapter);
    refresh();
  }
  @FXML public void selectWhitePawn() { select(PieceType.PAWN, PieceColor.WHITE); }
  @FXML public void selectWhiteKnight() { select(PieceType.KNIGHT, PieceColor.WHITE); }
  @FXML public void selectWhiteBishop() { select(PieceType.BISHOP, PieceColor.WHITE); }
  @FXML public void selectWhiteRook() { select(PieceType.ROOK, PieceColor.WHITE); }
  @FXML public void selectWhiteQueen() { select(PieceType.QUEEN, PieceColor.WHITE); }
  @FXML public void selectWhiteKing() { select(PieceType.KING, PieceColor.WHITE); }
  @FXML public void selectBlackPawn() { select(PieceType.PAWN, PieceColor.BLACK); }
  @FXML public void selectBlackKnight() { select(PieceType.KNIGHT, PieceColor.BLACK); }
  @FXML public void selectBlackBishop() { select(PieceType.BISHOP, PieceColor.BLACK); }
  @FXML public void selectBlackRook() { select(PieceType.ROOK, PieceColor.BLACK); }
  @FXML public void selectBlackQueen() { select(PieceType.QUEEN, PieceColor.BLACK); }
  @FXML public void selectBlackKing() { select(PieceType.KING, PieceColor.BLACK); }
  @FXML public void dragWhitePawn(MouseEvent event) { startPieceDrag(event, PieceType.PAWN, PieceColor.WHITE); }
  @FXML public void dragWhiteKnight(MouseEvent event) { startPieceDrag(event, PieceType.KNIGHT, PieceColor.WHITE); }
  @FXML public void dragWhiteBishop(MouseEvent event) { startPieceDrag(event, PieceType.BISHOP, PieceColor.WHITE); }
  @FXML public void dragWhiteRook(MouseEvent event) { startPieceDrag(event, PieceType.ROOK, PieceColor.WHITE); }
  @FXML public void dragWhiteQueen(MouseEvent event) { startPieceDrag(event, PieceType.QUEEN, PieceColor.WHITE); }
  @FXML public void dragWhiteKing(MouseEvent event) { startPieceDrag(event, PieceType.KING, PieceColor.WHITE); }
  @FXML public void dragBlackPawn(MouseEvent event) { startPieceDrag(event, PieceType.PAWN, PieceColor.BLACK); }
  @FXML public void dragBlackKnight(MouseEvent event) { startPieceDrag(event, PieceType.KNIGHT, PieceColor.BLACK); }
  @FXML public void dragBlackBishop(MouseEvent event) { startPieceDrag(event, PieceType.BISHOP, PieceColor.BLACK); }
  @FXML public void dragBlackRook(MouseEvent event) { startPieceDrag(event, PieceType.ROOK, PieceColor.BLACK); }
  @FXML public void dragBlackQueen(MouseEvent event) { startPieceDrag(event, PieceType.QUEEN, PieceColor.BLACK); }
  @FXML public void dragBlackKing(MouseEvent event) { startPieceDrag(event, PieceType.KING, PieceColor.BLACK); }
  @FXML public void onMetadataDraftChanged() { refreshEnPassantChoices(); }
  @FXML public void onClear() { editor.clear(); refresh(); }
  @FXML public void onReset() { editor.reset(); refresh(); }
  @FXML public void onFlip() { chessBoard.toggleOrientation(); }
  @FXML public void onApplyMetadata() { editor.configure(whiteToMove.isSelected() ? PieceColor.WHITE : PieceColor.BLACK, rights(), target(), halfmoveSpinner.getValue(), fullmoveSpinner.getValue()); refresh(); }
  @FXML public void onGenerateFen() { onApplyMetadata(); if (state().valid()) { fenText.setText(editor.fen().getValue()); statusLabel.setText("FEN generated"); } }
  @FXML public void onCopyFen() { onGenerateFen(); if (state().valid()) statusLabel.setText(clipboard.copyText(fenText.getText()) ? "FEN copied to clipboard" : "Unable to copy FEN"); }
  @FXML public void onImportFen() { try { editor.importFen(fenText.getText()); statusLabel.setText("FEN imported"); refresh(); } catch (IllegalArgumentException exception) { statusLabel.setText(message(exception, "The FEN is invalid.")); } }
  @FXML public void onOpenAnalysis() { Fen fen = validFen(); if (fen == null) return; var session = analysisSessions.createFenSession(fen); events.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Position opened from editor")); flow.show(UiScreenId.PGN_ANALYSIS); }
  @FXML public void onAddStudy() { Fen fen = validFen(); if (fen == null) return; if (currentUserService.activePlayerState().playerId().isEmpty()) { statusLabel.setText("Select an active player profile before adding a study chapter."); return; } var session = analysisSessions.createFenSession(fen); events.publish(new SelectStudyDestinationEvent(session.sessionId(), SelectStudyDestinationEvent.PostCopyDestination.OPEN_STUDY_WORKSPACE)); flow.show(UiScreenId.STUDY_DESTINATION); }
  @FXML public void onSaveChapterPosition() {
    if (chapterEditContext == null || validFen() == null) return;
    saveChapterPosition(false);
  }
  @FXML public void onBack() {
    if (chapterEditContext != null) returnToEditedChapter();
    else flow.show(UiScreenId.MAIN);
  }
  private void saveChapterPosition(boolean discardExistingMoves) {
    var result = studyService.updateChapterInitialPosition(
        new EditChapterInitialPositionCommand(
            chapterEditContext.ownerId(), chapterEditContext.studyId(), chapterEditContext.chapterId(),
            editor.state().position(), discardExistingMoves));
    if (result.requiresMoveReset()) {
      confirmMoveReset(result.discardedMoveCount());
      return;
    }
    returnToEditedChapter();
  }
  private void confirmMoveReset(int moveCount) {
    discardMovesConfirmation.setTitle("Replace chapter position?");
    discardMovesConfirmation.setMessage(
        "This chapter has " + moveCount + " moves. Saving will remove every move, variation and move comment.");
    discardMovesConfirmation.setAcceptText("Replace and clear moves");
    discardMovesConfirmation.setCancelText("Keep editing");
    discardMovesConfirmation.setOnAccept(event -> saveChapterPosition(true));
    discardMovesConfirmation.setOnCancel(event -> statusLabel.setText("Position not changed"));
    discardMovesConfirmation.show();
  }
  private void returnToEditedChapter() {
    StudyChapterPositionEditContext context = chapterEditContext;
    chapterEditContext = null;
    events.publish(new OpenStudyWorkspaceEvent(context.studyId(), context.chapterId()));
    flow.show(UiScreenId.STUDY_WORKSPACE);
  }
  private void select(PieceType type, PieceColor color) { selectedType = type; selectedColor = color; selectedPieceLabel.setText("Selected: " + color.name().toLowerCase() + " " + type.name().toLowerCase() + ". Click a square to place it; drag board pieces to move."); }
  private void refresh() { PositionEditorState state = state(); chessBoard.renderPosition(state.position()); whiteToMove.setSelected(state.position().activeColor() == PieceColor.WHITE); blackToMove.setSelected(state.position().activeColor() == PieceColor.BLACK); CastlingRights r = state.position().castlingRights(); whiteKingSide.setSelected(r.whiteKingSide()); whiteQueenSide.setSelected(r.whiteQueenSide()); blackKingSide.setSelected(r.blackKingSide()); blackQueenSide.setSelected(r.blackQueenSide()); refreshEnPassantChoices(); String currentTarget = state.position().enPassantTarget().map(Square::toAlgebraic).orElse("-"); enPassantCombo.setValue(enPassantCombo.getItems().contains(currentTarget) ? currentTarget : "-"); halfmoveSpinner.getValueFactory().setValue(state.position().halfmoveClock()); fullmoveSpinner.getValueFactory().setValue(state.position().fullmoveNumber()); if (!state.valid()) statusLabel.setText(state.validationMessage().orElseThrow()); }
  private PositionEditorState state() { return editor.state(); }
  private CastlingRights rights() { return new CastlingRights(whiteKingSide.isSelected(), whiteQueenSide.isSelected(), blackKingSide.isSelected(), blackQueenSide.isSelected()); }
  private Optional<Square> target() { return "-".equals(enPassantCombo.getValue()) ? Optional.empty() : Optional.of(Square.of(enPassantCombo.getValue())); }
  private Fen validFen() { onApplyMetadata(); if (!state().valid()) return null; return editor.fen(); }
  private static String message(Exception e, String fallback) { return e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage(); }
  private void refreshEnPassantChoices() {
    PieceColor activeColor = blackToMove.isSelected() ? PieceColor.BLACK : PieceColor.WHITE;
    String selected = enPassantCombo.getValue();
    var targets = editor.enPassantTargets(activeColor).stream().map(Square::toAlgebraic).toList();
    enPassantCombo.getItems().setAll("-");
    enPassantCombo.getItems().addAll(targets);
    enPassantCombo.setDisable(targets.isEmpty());
    enPassantCombo.setValue(selected != null && enPassantCombo.getItems().contains(selected) ? selected : "-");
  }
  private void startPieceDrag(MouseEvent event, PieceType type, PieceColor color) {
    select(type, color);
    Dragboard dragboard = ((Node) event.getSource()).startDragAndDrop(TransferMode.COPY);
    ClipboardContent content = new ClipboardContent();
    content.putString(new BoardPieceDragPayload(color, type).encode());
    dragboard.setContent(content);
    // Avoid JavaFX's default white snapshot of the palette label. Use the same artwork as the
    // board and size the drag proxy to a normal board square.
    Image dragImage =
        new Image(
            getClass()
                .getResource(pieceResource(color, type))
                .toExternalForm(),
            72,
            72,
            true,
            true);
    dragboard.setDragView(dragImage, 36, 36);
    event.consume();
  }

  private static String pieceResource(PieceColor color, PieceType type) {
    return "/chess-pieces/"
        + color.name().toLowerCase()
        + "-"
        + type.name().toLowerCase()
        + ".png";
  }
}
