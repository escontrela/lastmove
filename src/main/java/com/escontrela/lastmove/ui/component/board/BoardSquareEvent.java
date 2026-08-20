package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/** Presentation event for an editor action targeting one board square. */
public final class BoardSquareEvent extends Event {
  public static final EventType<BoardSquareEvent> SQUARE_ACTION = new EventType<>(Event.ANY, "BOARD_SQUARE_ACTION");
  private final Square square;
  public BoardSquareEvent(Object source, EventTarget target, Square square) {
    super(source, target, SQUARE_ACTION);
    this.square = Objects.requireNonNull(square, "square must not be null");
  }
  public Square getSquare() { return square; }
}
