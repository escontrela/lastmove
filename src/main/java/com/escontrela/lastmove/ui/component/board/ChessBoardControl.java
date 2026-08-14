package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import javafx.beans.property.ObjectProperty;
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

  private BoardTheme theme = BoardTheme.LASTMOVE;
  private ChessSoundService soundService;
  private final ObjectProperty<PositionSnapshot> position =
      new SimpleObjectProperty<>(this, "position");
  private final ObservableList<BoardArrow> arrows = FXCollections.observableArrayList();

  // 1. PROPIEDAD DEL EVENTO: Permite suscribir controladores externos
  private final ObjectProperty<EventHandler<BoardMoveEvent>> onMoveRequested =
      new SimpleObjectProperty<>(this, "onMoveRequested");

  public ChessBoardControl() {

    getStyleClass().add("chess-board");

    // Tamaño mínimo pequeño para permitir encogerse en ventanas reducidas;
    // el tamaño real lo determina el contenedor (ver binding en el controller de pantalla),
    // manteniendo siempre proporción 1:1 y un techo razonable (720) en pantallas grandes.
    setMinSize(240, 240);
    setPrefSize(720, 720);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new ChessBoardSkin(this);
  }

  public BoardTheme getTheme() {
    return theme;
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

  public void setTheme(BoardTheme theme) {
    this.theme = theme;
    if (getSkin() != null) {
      getSkin().dispose();
      setSkin(createDefaultSkin());
    }
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

  /**
   * This method make the forwarding of the move input event to the subscribed event handler, for
   * instance the PgnAnalysisScreenController. It is called from the ChessBoardSkin when a move is
   * detected.
   *
   * @param moveInput The move input detected on the board.
   */
  public void handleBoardMoveInput(BoardMoveInput moveInput) {

    EventHandler<BoardMoveEvent> handler = getOnMoveRequested();

    if (handler != null) {

      handler.handle(new BoardMoveEvent(this, moveInput));
    }
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
}
