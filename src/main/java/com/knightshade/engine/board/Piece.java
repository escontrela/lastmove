package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;

/**
 * Compact encoding of one chess piece as an {@code int}.
 *
 * <p>The low three bits hold the piece type (1..6), bit 3 is the color flag (0 = white, 1 =
 * black), and 0 represents an empty square. This keeps the board as a flat {@code int[64]} while
 * still mapping to and from the shared {@link PieceType} and {@link PieceColor} value objects.
 */
public final class Piece {

  public static final int NONE = 0;
  public static final int PAWN = 1;
  public static final int KNIGHT = 2;
  public static final int BISHOP = 3;
  public static final int ROOK = 4;
  public static final int QUEEN = 5;
  public static final int KING = 6;

  private static final int TYPE_MASK = 7;
  private static final int COLOR_FLAG = 8;

  private Piece() {}

  /** Encodes a colored piece. */
  public static int of(PieceColor color, PieceType type) {
    return typeIndex(type) | (color == PieceColor.BLACK ? COLOR_FLAG : 0);
  }

  /** Returns the type of an encoded piece, rejecting empty squares. */
  public static PieceType type(int piece) {
    return switch (piece & TYPE_MASK) {
      case PAWN -> PieceType.PAWN;
      case KNIGHT -> PieceType.KNIGHT;
      case BISHOP -> PieceType.BISHOP;
      case ROOK -> PieceType.ROOK;
      case QUEEN -> PieceType.QUEEN;
      case KING -> PieceType.KING;
      default -> throw new IllegalArgumentException("Not a piece: " + piece);
    };
  }

  /** Returns the color of an encoded piece, rejecting empty squares. */
  public static PieceColor color(int piece) {
    if (piece == NONE) {
      throw new IllegalArgumentException("An empty square has no color");
    }
    return (piece & COLOR_FLAG) == 0 ? PieceColor.WHITE : PieceColor.BLACK;
  }

  /** Returns whether the piece is white. */
  public static boolean isWhite(int piece) {
    return (piece & COLOR_FLAG) == 0;
  }

  /** Returns whether the encoded piece matches both the given color and type. */
  public static boolean is(int piece, PieceColor color, PieceType type) {
    return piece != NONE && (piece & TYPE_MASK) == typeIndex(type) && color(piece) == color;
  }

  /** Returns the FEN character for an encoded piece. */
  public static char toFenChar(int piece) {
    char symbol =
        switch (piece & TYPE_MASK) {
          case PAWN -> 'p';
          case KNIGHT -> 'n';
          case BISHOP -> 'b';
          case ROOK -> 'r';
          case QUEEN -> 'q';
          case KING -> 'k';
          default -> throw new IllegalArgumentException("Not a piece: " + piece);
        };
    return isWhite(piece) ? Character.toUpperCase(symbol) : symbol;
  }

  private static int typeIndex(PieceType type) {
    return switch (type) {
      case PAWN -> PAWN;
      case KNIGHT -> KNIGHT;
      case BISHOP -> BISHOP;
      case ROOK -> ROOK;
      case QUEEN -> QUEEN;
      case KING -> KING;
    };
  }
}
