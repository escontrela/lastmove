package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * A reusable JavaFX control that renders a chess board.
 *
 * <p>The control receives a position (as a FEN string or a view model) and renders it using {@link
 * ChessBoardSkin}. It also owns temporary visual calculation arrows drawn with the secondary mouse
 * button. It owns only rendering and user interaction – it does not validate chess rules or parse
 * PGN.
 */
public class ChessBoardControl extends Control {

  private final ObjectProperty<BoardAppearancePreset> appearancePreset =
      new SimpleObjectProperty<>(this, "appearancePreset", BoardAppearancePreset.STANDARD);
  private ChessSoundService soundService;
  private final ObjectProperty<PositionSnapshot> position =
      new SimpleObjectProperty<>(this, "position");
  private final ObservableList<BoardArrow> arrows = FXCollections.observableArrayList();
  private final BooleanProperty flipped = new SimpleBooleanProperty(this, "flipped", false);
  private final BooleanProperty visualEffectsEnabled =
      new SimpleBooleanProperty(this, "visualEffectsEnabled", true);
  private final ObjectProperty<Square> hintSquare = new SimpleObjectProperty<>(this, "hintSquare");
  private final ObservableList<Square> threatenedSquares = FXCollections.observableArrayList();

  // 1. PROPIEDAD DEL EVENTO: Permite suscribir controladores externos
  private final ObjectProperty<EventHandler<BoardMoveEvent>> onMoveRequested =
      new SimpleObjectProperty<>(this, "onMoveRequested");
  private final ObjectProperty<EventHandler<BoardPromotionEvent>> onPromotionRequested =
      new SimpleObjectProperty<>(this, "onPromotionRequested");
  private final BooleanProperty editorMode = new SimpleBooleanProperty(this, "editorMode", false);
  private final ObjectProperty<EventHandler<BoardSquareEvent>> onPieceRemovalRequested =
      new SimpleObjectProperty<>(this, "onPieceRemovalRequested");
  private final ObjectProperty<EventHandler<BoardSquareEvent>> onEditorSquareRequested =
      new SimpleObjectProperty<>(this, "onEditorSquareRequested");
  private final ObjectProperty<EventHandler<BoardPieceDropEvent>> onPieceDropped =
      new SimpleObjectProperty<>(this, "onPieceDropped");

  public ChessBoardControl() {

    getStyleClass().add("chess-board");

    // Tamaño mínimo pequeño para permitir encogerse en ventanas reducidas;
    // el tamaño real lo determina el contenedor (ver binding en el controller de pantalla),
    // manteniendo siempre proporción 1:1 y un techo razonable (720) en pantallas grandes.
    setMinSize(240, 240);
    setPrefSize(720, 720);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    visualEffectsEnabled.addListener((observable, oldValue, enabled) -> updateVisualEffectsStyle());
    appearancePreset.addListener(
        (observable, oldPreset, newPreset) -> updateAppearancePresetStyle(newPreset));
    updateVisualEffectsStyle();
    updateAppearancePresetStyle(appearancePreset.get());
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new ChessBoardSkin(this);
  }

  public BoardTheme getTheme() {
    return getAppearancePreset().boardTheme();
  }

  public final ObjectProperty<BoardAppearancePreset> appearancePresetProperty() {
    return appearancePreset;
  }

  public final BoardAppearancePreset getAppearancePreset() {
    return appearancePreset.get();
  }

  /** Applies a complete presentation preset without changing chess state or interaction handlers. */
  public final void setAppearancePreset(BoardAppearancePreset preset) {
    appearancePreset.set(Objects.requireNonNull(preset, "preset must not be null"));
  }

  /** Updates the complete board state that the skin must render. */
  public final void renderPosition(PositionSnapshot positionSnapshot) {
    position.set(positionSnapshot);
  }

  public final ObjectProperty<PositionSnapshot> positionProperty() {
    return position;
  }

  public final PositionSnapshot getPosition() {
    return position.get();
  }

  /** Presentation-only orientation flag; {@code false} keeps White at the bottom. */
  public final BooleanProperty flippedProperty() {
    return flipped;
  }

  public final boolean isFlipped() {
    return flipped.get();
  }

  /** Sets the visual board orientation without modifying its position or chess state. */
  public final void setFlipped(boolean value) {
    flipped.set(value);
  }

  /** Rotates the rendered board by 180 degrees and returns the new orientation. */
  public final boolean toggleOrientation() {
    setFlipped(!isFlipped());
    return isFlipped();
  }

  /** Enables the optional board halo and square gradients without changing any chess state. */
  public final BooleanProperty visualEffectsEnabledProperty() {
    return visualEffectsEnabled;
  }

  public final boolean isVisualEffectsEnabled() {
    return visualEffectsEnabled.get();
  }

  public final void setVisualEffectsEnabled(boolean enabled) {
    visualEffectsEnabled.set(enabled);
  }

  /** Highlights a source square as presentation-only guidance for the next move. */
  public final void setHintSquare(Square square) {
    hintSquare.set(square);
  }

  public final void clearHintSquare() {
    hintSquare.set(null);
  }

  public final ObjectProperty<Square> hintSquareProperty() {
    return hintSquare;
  }
  public final void setThreatenedSquares(Set<Square> squares) { threatenedSquares.setAll(squares); }
  public final void clearThreatenedSquares() { threatenedSquares.clear(); }
  ObservableList<Square> observableThreatenedSquares() { return threatenedSquares; }

  public final Square getHintSquare() {
    return hintSquare.get();
  }

  /** Replaces the visual calculation arrows without changing the rendered chess position. */
  public final void setArrows(java.util.List<BoardArrow> values) {
    arrows.setAll(java.util.List.copyOf(values));
  }

  /** Returns an immutable copy of the current visual calculation arrows. */
  public final java.util.List<BoardArrow> getArrows() {
    return java.util.List.copyOf(arrows);
  }

  /** Removes every visual calculation arrow from the board. */
  public final void clearArrows() {
    arrows.clear();
  }

  ObservableList<BoardArrow> observableArrows() {
    return arrows;
  }

  void toggleArrow(BoardArrow arrow) {
    if (!arrows.remove(arrow)) {
      arrows.add(arrow);
    }
  }

  /** Installs the presentation service used by this control's skin for move feedback. */
  public final void setSoundService(ChessSoundService soundService) {
    this.soundService = soundService;
  }

  void playSound(ChessSound sound) {
    if (soundService != null) {
      soundService.play(sound);
    }
  }

  /**
   * @deprecated Use {@link #setAppearancePreset(BoardAppearancePreset)}. Themes no longer own a
   *     complete board appearance.
   */
  @Deprecated
  public void setTheme(BoardTheme theme) {
    BoardTheme requiredTheme = Objects.requireNonNull(theme, "theme must not be null");
    for (BoardAppearancePreset preset : BoardAppearancePreset.values()) {
      if (preset.boardTheme() == requiredTheme) {
        setAppearancePreset(preset);
        return;
      }
    }
    throw new IllegalArgumentException("No board appearance preset uses theme " + requiredTheme);
  }

  private void updateVisualEffectsStyle() {
    getStyleClass().remove("board-visual-effects");
    if (isVisualEffectsEnabled()) {
      getStyleClass().add("board-visual-effects");
    }
  }

  private void updateAppearancePresetStyle(BoardAppearancePreset preset) {
    getStyleClass().remove("board-v2");
    if (preset.framed()) {
      getStyleClass().add("board-v2");
    }
    requestLayout();
  }

  // 2. MÉTODOS DE LA API DE PROPIEDADES DE JAVAFX
  public final ObjectProperty<EventHandler<BoardMoveEvent>> onMoveRequestedProperty() {
    return onMoveRequested;
  }

  public final void setOnMoveRequested(EventHandler<BoardMoveEvent> value) {
    onMoveRequested.set(value);
  }

  public final EventHandler<BoardMoveEvent> getOnMoveRequested() {
    return onMoveRequested.get();
  }

  /** Property notified when a board gesture needs an explicit promotion piece before submission. */
  public final ObjectProperty<EventHandler<BoardPromotionEvent>> onPromotionRequestedProperty() {
    return onPromotionRequested;
  }

  /** Registers the UI handler that displays a promotion chooser for an eligible pawn move. */
  public final void setOnPromotionRequested(EventHandler<BoardPromotionEvent> value) {
    onPromotionRequested.set(value);
  }

  public final EventHandler<BoardPromotionEvent> getOnPromotionRequested() {
    return onPromotionRequested.get();
  }

  /** Enables free-authoring input. It suppresses analysis arrows and normal promotion prompts. */
  public final BooleanProperty editorModeProperty() {
    return editorMode;
  }

  public final boolean isEditorMode() {
    return editorMode.get();
  }

  public final void setEditorMode(boolean value) {
    editorMode.set(value);
  }

  public final ObjectProperty<EventHandler<BoardSquareEvent>> onPieceRemovalRequestedProperty() {
    return onPieceRemovalRequested;
  }

  public final void setOnPieceRemovalRequested(EventHandler<BoardSquareEvent> value) {
    onPieceRemovalRequested.set(value);
  }

  public final void handlePieceRemoval(Square square) {
    EventHandler<BoardSquareEvent> handler = onPieceRemovalRequested.get();
    if (handler != null) handler.handle(new BoardSquareEvent(this, this, square));
  }

  public final void setOnEditorSquareRequested(EventHandler<BoardSquareEvent> value) {
    onEditorSquareRequested.set(value);
  }

  public final void handleEditorSquare(Square square) {

    EventHandler<BoardSquareEvent> handler = onEditorSquareRequested.get();
    if (handler != null) handler.handle(new BoardSquareEvent(this, this, square));
  }

  public final void setOnPieceDropped(EventHandler<BoardPieceDropEvent> value) {
    onPieceDropped.set(value);
  }

  public final void handlePieceDrop(Square square, BoardPieceDragPayload payload) {
    EventHandler<BoardPieceDropEvent> handler = onPieceDropped.get();
    if (handler != null) {
      handler.handle(new BoardPieceDropEvent(this, this, square, payload.type(), payload.color()));
    }
  }

  /**
   * This method make the forwarding of the move input event to the subscribed event handler, for
   * instance the PgnAnalysisScreenController. It is called from the ChessBoardSkin when a move is
   * detected.
   *
   * @param moveInput The move input detected on the board.
   */
  public void handleBoardMoveInput(BoardMoveInput moveInput) {

    BoardMoveInput requiredMoveInput =
        Objects.requireNonNull(moveInput, "moveInput must not be null");
    Optional<PieceColor> promotionColor = promotionColorFor(requiredMoveInput);
    EventHandler<BoardPromotionEvent> promotionHandler = getOnPromotionRequested();
    if (promotionColor.isPresent()
        && requiredMoveInput.promotionPiece().isEmpty()
        && promotionHandler != null) {
      promotionHandler.handle(
          new BoardPromotionEvent(this, requiredMoveInput, promotionColor.orElseThrow()));
      return;
    }

    EventHandler<BoardMoveEvent> handler = getOnMoveRequested();

    if (handler != null) {

      handler.handle(new BoardMoveEvent(this, requiredMoveInput));
    }
  }

  /**
   * Detects only the presentation condition for displaying a promotion chooser.
   *
   * <p>The rules engine remains authoritative: this method merely observes that the active pawn
   * represented on the board is being moved to its final rank. Illegal moves still reach the
   * application flow after the user has made a choice.
   */
  private Optional<PieceColor> promotionColorFor(BoardMoveInput moveInput) {
    PositionSnapshot currentPosition = getPosition();
    if (currentPosition == null || moveInput.promotionPiece().isPresent()) {
      return Optional.empty();
    }
    return currentPosition.pieces().stream()
        .filter(piece -> piece.square().equals(moveInput.fromSquare()))
        .filter(piece -> piece.type() == PieceType.PAWN)
        .filter(piece -> piece.color() == currentPosition.activeColor())
        .filter(piece -> reachesPromotionRank(piece, moveInput))
        .map(PositionPiece::color)
        .findFirst();
  }

  private boolean reachesPromotionRank(PositionPiece pawn, BoardMoveInput moveInput) {
    return (pawn.color() == PieceColor.WHITE && moveInput.toSquare().getRank() == 7)
        || (pawn.color() == PieceColor.BLACK && moveInput.toSquare().getRank() == 0);
  }

  /** 3. EVENTO DE DOMINIO UI: Estructura inmutable para el transporte de eventos en JavaFX */
  public static class BoardMoveEvent extends javafx.event.Event {

    public static final javafx.event.EventType<BoardMoveEvent> MOVE_REQUESTED =
        new javafx.event.EventType<>(javafx.event.Event.ANY, "MOVE_REQUESTED");

    private final BoardMoveInput moveInput;

    public BoardMoveEvent(ChessBoardControl source, BoardMoveInput moveInput) {

      super(source, NULL_SOURCE_TARGET, MOVE_REQUESTED);
      this.moveInput = moveInput;
    }

    public BoardMoveInput getMoveInput() {

      return moveInput;
    }
  }

  /** UI event requesting a target piece before a pawn move to the final rank is submitted. */
  public static class BoardPromotionEvent extends javafx.event.Event {

    public static final javafx.event.EventType<BoardPromotionEvent> PROMOTION_REQUESTED =
        new javafx.event.EventType<>(javafx.event.Event.ANY, "PROMOTION_REQUESTED");

    private final BoardMoveInput moveInput;
    private final PieceColor promotingColor;

    public BoardPromotionEvent(
        ChessBoardControl source, BoardMoveInput moveInput, PieceColor promotingColor) {
      super(source, NULL_SOURCE_TARGET, PROMOTION_REQUESTED);
      this.moveInput = Objects.requireNonNull(moveInput, "moveInput must not be null");
      this.promotingColor =
          Objects.requireNonNull(promotingColor, "promotingColor must not be null");
    }

    /** Returns the incomplete board gesture that must be completed with a promotion piece. */
    public BoardMoveInput getMoveInput() {
      return moveInput;
    }

    /** Returns the colour whose pawn is promoting. */
    public PieceColor getPromotingColor() {
      return promotingColor;
    }
  }
}
