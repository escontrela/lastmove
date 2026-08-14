package com.escontrela.lastmove.ui.model;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a move on the chess board.
 *
 * @param fromSquare the square from which the piece is moving
 * @param toSquare the square to which the piece is moving
 * @param promotionPiece the piece type to which a pawn is promoted, if applicable
 */
public record BoardMoveInput(
    Square fromSquare, Square toSquare, Optional<PieceType> promotionPiece) {

  public BoardMoveInput {
    Objects.requireNonNull(fromSquare, "fromSquare must not be null");
    Objects.requireNonNull(toSquare, "toSquare must not be null");
    promotionPiece = Objects.requireNonNull(promotionPiece, "promotionPiece must not be null");
  }

  public static BoardMoveInput from(Square fromSquare, Square toSquare) {

    return new BoardMoveInput(fromSquare, toSquare, Optional.empty());
  }

  /** Returns the same board gesture completed with the user's explicit promotion choice. */
  public BoardMoveInput withPromotion(PieceType pieceType) {
    return new BoardMoveInput(fromSquare, toSquare, Optional.of(Objects.requireNonNull(pieceType)));
  }
}
