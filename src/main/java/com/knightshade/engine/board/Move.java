package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import java.util.Objects;

/**
 * One chess move in engine coordinates.
 *
 * <p>{@code promotion} is null for non-promotion moves, {@code captured} is null for quiet moves,
 * and {@link MoveFlag} records only the aspects that change make/unmake geometry (double pawn
 * push, en passant and the two castling directions).
 */
public record Move(
    Square from, Square to, PieceType promotion, MoveFlag flag, PieceType captured) {

  public Move {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(flag, "flag must not be null");
  }

  public boolean isCapture() {
    return captured != null;
  }

  public boolean isPromotion() {
    return promotion != null;
  }

  public boolean isCastle() {
    return flag == MoveFlag.KING_CASTLE || flag == MoveFlag.QUEEN_CASTLE;
  }

  public boolean isEnPassant() {
    return flag == MoveFlag.EN_PASSANT;
  }

  /** Returns the UCI coordinate form, for example {@code e2e4} or {@code e7e8q}. */
  public String toUci() {
    return from.toAlgebraic() + to.toAlgebraic() + promotionSymbol();
  }

  private String promotionSymbol() {
    if (promotion == null) {
      return "";
    }
    return switch (promotion) {
      case QUEEN -> "q";
      case ROOK -> "r";
      case BISHOP -> "b";
      case KNIGHT -> "n";
      case PAWN, KING -> "";
    };
  }

  @Override
  public String toString() {
    return toUci();
  }
}
