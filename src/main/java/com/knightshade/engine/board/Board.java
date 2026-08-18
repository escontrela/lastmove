package com.knightshade.engine.board;

import static com.knightshade.engine.board.Position.indexOf;
import static com.knightshade.engine.board.Position.squareOf;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mutable mailbox board used as the search workspace.
 *
 * <p>Pieces live in a flat {@code int[64]} array (rank-major, index = rank * 8 + file). {@link
 * #make(Move)} and {@link #unmake()} mutate the board in place and are used by move generation to
 * test legality and by the search to walk the tree. Attack detection is computed on demand, so no
 * incremental update tables are needed for v0.
 */
public final class Board implements Position {

  private static final Square WHITE_KING_ROOK = Square.of(7, 0);
  private static final Square WHITE_QUEEN_ROOK = Square.of(0, 0);
  private static final Square BLACK_KING_ROOK = Square.of(7, 7);
  private static final Square BLACK_QUEEN_ROOK = Square.of(0, 7);

  private final int[] pieces = new int[64];
  private final Deque<Undo> undoStack = new ArrayDeque<>();

  private PieceColor sideToMove = PieceColor.WHITE;
  private CastlingRights castlingRights = CastlingRights.none();
  private Square enPassantTarget;
  private int halfmoveClock;
  private int fullmoveNumber = 1;

  /** Creates an empty board; the FEN parser fills state through the package setters below. */
  static Board empty() {
    return new Board();
  }

  @Override
  public PieceColor sideToMove() {
    return sideToMove;
  }

  @Override
  public CastlingRights castlingRights() {
    return castlingRights;
  }

  @Override
  public Square enPassantTarget() {
    return enPassantTarget;
  }

  @Override
  public int halfmoveClock() {
    return halfmoveClock;
  }

  @Override
  public int fullmoveNumber() {
    return fullmoveNumber;
  }

  @Override
  public int pieceAt(int index) {
    return pieces[index];
  }

  void setPiece(Square square, int piece) {
    pieces[indexOf(square)] = piece;
  }

  void setSideToMove(PieceColor color) {
    sideToMove = color;
  }

  void setCastlingRights(CastlingRights rights) {
    castlingRights = rights;
  }

  void setEnPassantTarget(Square square) {
    enPassantTarget = square;
  }

  void setHalfmoveClock(int value) {
    halfmoveClock = value;
  }

  void setFullmoveNumber(int value) {
    fullmoveNumber = value;
  }

  /** Applies a move, recording everything needed by {@link #unmake()}. */
  public void make(Move move) {
    Square from = move.from();
    Square to = move.to();
    int movingPiece = pieces[indexOf(from)];

    int capturedPiece;
    Square capturedSquare;
    if (move.isEnPassant()) {
      capturedSquare = Square.of(to.getFile(), from.getRank());
      capturedPiece = pieces[indexOf(capturedSquare)];
    } else {
      capturedSquare = to;
      capturedPiece = pieces[indexOf(to)];
    }

    undoStack.push(
        new Undo(move, capturedPiece, capturedSquare, castlingRights, enPassantTarget,
            halfmoveClock, fullmoveNumber));

    PieceColor movingColor = Piece.color(movingPiece);
    pieces[indexOf(from)] = Piece.NONE;
    pieces[indexOf(to)] =
        Piece.of(movingColor, move.isPromotion() ? move.promotion() : Piece.type(movingPiece));
    if (move.isEnPassant()) {
      pieces[indexOf(capturedSquare)] = Piece.NONE;
    }
    if (move.isCastle()) {
      moveRookForCastle(movingColor, move.flag() == MoveFlag.KING_CASTLE);
    }

    sideToMove = sideToMove.opposite();
    castlingRights = castlingRightsAfter(move, movingColor, movingPiece, capturedSquare);
    enPassantTarget =
        move.flag() == MoveFlag.DOUBLE_PAWN_PUSH
            ? Square.of(from.getFile(), (from.getRank() + to.getRank()) / 2)
            : null;
    boolean resetsClock = Piece.type(movingPiece) == PieceType.PAWN || move.isCapture();
    halfmoveClock = resetsClock ? 0 : halfmoveClock + 1;
    if (movingColor == PieceColor.BLACK) {
      fullmoveNumber++;
    }
  }

  /** Reverts the most recent {@link #make(Move)} call, restoring board and state exactly. */
  public void unmake() {
    Undo undo = undoStack.pop();
    Move move = undo.move;
    Square from = move.from();
    Square to = move.to();

    int placedPiece = pieces[indexOf(to)];
    PieceColor color = Piece.color(placedPiece);
    PieceType restoredType = move.isPromotion() ? PieceType.PAWN : Piece.type(placedPiece);
    pieces[indexOf(from)] = Piece.of(color, restoredType);
    pieces[indexOf(to)] = undo.capturedPiece;
    if (move.isEnPassant()) {
      pieces[indexOf(to)] = Piece.NONE;
      pieces[indexOf(undo.capturedSquare)] = undo.capturedPiece;
    }
    if (move.isCastle()) {
      moveRookBackForCastle(color, move.flag() == MoveFlag.KING_CASTLE);
    }

    sideToMove = sideToMove.opposite();
    castlingRights = undo.castlingRights;
    enPassantTarget = undo.enPassantTarget;
    halfmoveClock = undo.halfmoveClock;
    fullmoveNumber = undo.fullmoveNumber;
  }

  /** Returns the square of the given color's king. */
  public Square kingSquare(PieceColor color) {
    for (int index = 0; index < 64; index++) {
      if (Piece.is(pieces[index], color, PieceType.KING)) {
        return squareOf(index);
      }
    }
    throw new IllegalStateException("The position has no " + color + " king");
  }

  /** Returns whether the given color is currently in check. */
  public boolean inCheck(PieceColor color) {
    return isSquareAttacked(kingSquare(color), color.opposite());
  }

  /**
   * Returns whether the given square is attacked by any piece of the given color.
   *
   * <p>Sliding attacks are found by walking outward from the target, so no precomputed attack
   * tables are required.
   */
  public boolean isSquareAttacked(Square target, PieceColor byColor) {
    int file = target.getFile();
    int rank = target.getRank();

    if (byColor == PieceColor.WHITE) {
      if (hasPieceAt(file - 1, rank - 1, PieceColor.WHITE, PieceType.PAWN)
          || hasPieceAt(file + 1, rank - 1, PieceColor.WHITE, PieceType.PAWN)) {
        return true;
      }
    } else {
      if (hasPieceAt(file - 1, rank + 1, PieceColor.BLACK, PieceType.PAWN)
          || hasPieceAt(file + 1, rank + 1, PieceColor.BLACK, PieceType.PAWN)) {
        return true;
      }
    }

    for (int[] offset : KNIGHT_OFFSETS) {
      if (hasPieceAt(file + offset[0], rank + offset[1], byColor, PieceType.KNIGHT)) {
        return true;
      }
    }
    for (int[] offset : KING_OFFSETS) {
      if (hasPieceAt(file + offset[0], rank + offset[1], byColor, PieceType.KING)) {
        return true;
      }
    }

    for (int[] direction : ORTHOGONAL_DIRECTIONS) {
      if (orthogonalSliderAttacks(file, rank, direction, byColor)) {
        return true;
      }
    }
    for (int[] direction : DIAGONAL_DIRECTIONS) {
      if (diagonalSliderAttacks(file, rank, direction, byColor)) {
        return true;
      }
    }
    return false;
  }

  /** Serializes the position to FEN, primarily for round-trip tests and diagnostics. */
  public String toFen() {
    StringBuilder fen = new StringBuilder();
    for (int rank = 7; rank >= 0; rank--) {
      int empty = 0;
      for (int file = 0; file < 8; file++) {
        int piece = pieces[rank * 8 + file];
        if (piece == Piece.NONE) {
          empty++;
        } else {
          if (empty > 0) {
            fen.append(empty);
            empty = 0;
          }
          fen.append(Piece.toFenChar(piece));
        }
      }
      if (empty > 0) {
        fen.append(empty);
      }
      if (rank > 0) {
        fen.append('/');
      }
    }
    fen.append(' ').append(sideToMove == PieceColor.WHITE ? 'w' : 'b');
    fen.append(' ');
    int castlingStart = fen.length();
    if (castlingRights.whiteKingSide()) {
      fen.append('K');
    }
    if (castlingRights.whiteQueenSide()) {
      fen.append('Q');
    }
    if (castlingRights.blackKingSide()) {
      fen.append('k');
    }
    if (castlingRights.blackQueenSide()) {
      fen.append('q');
    }
    if (fen.length() == castlingStart) {
      fen.append('-');
    }
    fen.append(' ')
        .append(enPassantTarget == null ? "-" : enPassantTarget.toAlgebraic())
        .append(' ')
        .append(halfmoveClock)
        .append(' ')
        .append(fullmoveNumber);
    return fen.toString();
  }

  private void moveRookForCastle(PieceColor color, boolean kingSide) {
    int rank = color == PieceColor.WHITE ? 0 : 7;
    int rookFrom = indexOf(Square.of(kingSide ? 7 : 0, rank));
    int rookTo = indexOf(Square.of(kingSide ? 5 : 3, rank));
    pieces[rookTo] = pieces[rookFrom];
    pieces[rookFrom] = Piece.NONE;
  }

  private void moveRookBackForCastle(PieceColor color, boolean kingSide) {
    int rank = color == PieceColor.WHITE ? 0 : 7;
    int rookTo = indexOf(Square.of(kingSide ? 5 : 3, rank));
    int rookFrom = indexOf(Square.of(kingSide ? 7 : 0, rank));
    pieces[rookFrom] = pieces[rookTo];
    pieces[rookTo] = Piece.NONE;
  }

  private CastlingRights castlingRightsAfter(
      Move move, PieceColor movingColor, int movingPiece, Square capturedSquare) {
    boolean wk = castlingRights.whiteKingSide();
    boolean wq = castlingRights.whiteQueenSide();
    boolean bk = castlingRights.blackKingSide();
    boolean bq = castlingRights.blackQueenSide();

    if (Piece.type(movingPiece) == PieceType.KING) {
      if (movingColor == PieceColor.WHITE) {
        wk = false;
        wq = false;
      } else {
        bk = false;
        bq = false;
      }
    }
    Square from = move.from();
    if (from.equals(WHITE_KING_ROOK)) {
      wk = false;
    } else if (from.equals(WHITE_QUEEN_ROOK)) {
      wq = false;
    } else if (from.equals(BLACK_KING_ROOK)) {
      bk = false;
    } else if (from.equals(BLACK_QUEEN_ROOK)) {
      bq = false;
    }

    if (capturedSquare.equals(WHITE_KING_ROOK)) {
      wk = false;
    } else if (capturedSquare.equals(WHITE_QUEEN_ROOK)) {
      wq = false;
    } else if (capturedSquare.equals(BLACK_KING_ROOK)) {
      bk = false;
    } else if (capturedSquare.equals(BLACK_QUEEN_ROOK)) {
      bq = false;
    }

    return new CastlingRights(wk, wq, bk, bq);
  }

  private boolean hasPieceAt(int file, int rank, PieceColor color, PieceType type) {
    if (file < 0 || file > 7 || rank < 0 || rank > 7) {
      return false;
    }
    return Piece.is(pieces[rank * 8 + file], color, type);
  }

  private boolean orthogonalSliderAttacks(
      int file, int rank, int[] direction, PieceColor byColor) {
    int nextFile = file + direction[0];
    int nextRank = rank + direction[1];
    while (nextFile >= 0 && nextFile < 8 && nextRank >= 0 && nextRank < 8) {
      int piece = pieces[nextRank * 8 + nextFile];
      if (piece != Piece.NONE) {
        return Piece.color(piece) == byColor
            && (Piece.type(piece) == PieceType.ROOK || Piece.type(piece) == PieceType.QUEEN);
      }
      nextFile += direction[0];
      nextRank += direction[1];
    }
    return false;
  }

  private boolean diagonalSliderAttacks(
      int file, int rank, int[] direction, PieceColor byColor) {
    int nextFile = file + direction[0];
    int nextRank = rank + direction[1];
    while (nextFile >= 0 && nextFile < 8 && nextRank >= 0 && nextRank < 8) {
      int piece = pieces[nextRank * 8 + nextFile];
      if (piece != Piece.NONE) {
        return Piece.color(piece) == byColor
            && (Piece.type(piece) == PieceType.BISHOP || Piece.type(piece) == PieceType.QUEEN);
      }
      nextFile += direction[0];
      nextRank += direction[1];
    }
    return false;
  }

  private static final int[][] KNIGHT_OFFSETS = {
    {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
  };

  private static final int[][] KING_OFFSETS = {
    {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
  };

  private static final int[][] ORTHOGONAL_DIRECTIONS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}
  };

  private static final int[][] DIAGONAL_DIRECTIONS = {
    {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private record Undo(
      Move move,
      int capturedPiece,
      Square capturedSquare,
      CastlingRights castlingRights,
      Square enPassantTarget,
      int halfmoveClock,
      int fullmoveNumber) {}
}
