package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.ui.model.BoardMoveInput;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * A reusable JavaFX control that renders a chess board.
 *
 * <p>The control receives a position (as a FEN string or a view model) and renders it using {@link
 * ChessBoardSkin}. It owns only rendering and user interaction – it does not validate chess rules
 * or parse PGN.
 */
public class ChessBoardControl extends Control {

  private BoardTheme theme = BoardTheme.LASTMOVE;

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
   * Recibe la intención de la skin y la despacha de forma asíncrona hacia el manejador de eventos.
   */
  public void handleBoardMoveInput(BoardMoveInput moveInput) {

    System.out.println("Movimiento detectado: " + moveInput);

    EventHandler<BoardMoveEvent> handler = getOnMoveRequested();
    if (handler != null) {
      // Disparamos nuestro evento personalizado
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
