package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/** Presentation event emitted when a palette piece is dropped on the editor board. */
public final class BoardPieceDropEvent extends Event {
  public static final EventType<BoardPieceDropEvent> PIECE_DROP =
      new EventType<>(Event.ANY, "BOARD_PIECE_DROP");

  private final Square square;
  private final PieceType pieceType;
  private final PieceColor pieceColor;

  public BoardPieceDropEvent(
      Object source, EventTarget target, Square square, PieceType pieceType, PieceColor pieceColor) {
    super(source, target, PIECE_DROP);
    this.square = Objects.requireNonNull(square, "square must not be null");
    this.pieceType = Objects.requireNonNull(pieceType, "pieceType must not be null");
    this.pieceColor = Objects.requireNonNull(pieceColor, "pieceColor must not be null");
  }

  public Square getSquare() { return square; }
  public PieceType getPieceType() { return pieceType; }
  public PieceColor getPieceColor() { return pieceColor; }
}
