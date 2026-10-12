package com.knightshade.engine.see;

import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.knightshade.engine.evaluation.PieceValues;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;

/**
 * Static exchange evaluation: the net material gain of a capture exchange on one square.
 *
 * <p>The evaluation simulates the move on a local copy of the board and then alternates
 * recaptures, always using the least valuable attacker. The side may stand pat, so a negative
 * result marks a losing capture that can be pruned or demoted.
 */
public final class See {

  private See() {}

  /** Net material gain for the side making {@code move}, in centipawns. */
  public static int evaluate(Position position, Move move) {
    int[] board = new int[64];
    int whiteKing = -1;
    int blackKing = -1;
    for (int i = 0; i < 64; i++) {
      board[i] = position.pieceAt(i);
      if (Piece.is(board[i], PieceColor.WHITE, PieceType.KING)) whiteKing = i;
      if (Piece.is(board[i], PieceColor.BLACK, PieceType.KING)) blackKing = i;
    }

    int fromIndex = Position.indexOf(move.from());
    int toIndex = Position.indexOf(move.to());
    int movingPiece = board[fromIndex];
    PieceColor mover = Piece.color(movingPiece);

    int capturedValue;
    if (move.isEnPassant()) {
      capturedValue = PieceValues.PAWN;
      int capturedIndex = Position.indexOf(Square.of(move.to().getFile(), move.from().getRank()));
      board[capturedIndex] = Piece.NONE;
    } else {
      capturedValue = board[toIndex] == Piece.NONE ? 0 : PieceValues.of(Piece.type(board[toIndex]));
    }

    board[fromIndex] = Piece.NONE;
    board[toIndex] =
        Piece.of(mover, move.isPromotion() ? move.promotion() : Piece.type(movingPiece));
    if (Piece.type(movingPiece) == PieceType.KING) {
      if (mover == PieceColor.WHITE) whiteKing = toIndex;
      else blackKing = toIndex;
    }

    int promotionGain =
        move.isPromotion()
            ? PieceValues.of(move.promotion()) - PieceValues.PAWN
            : 0;
    return capturedValue + promotionGain
        - seeCapture(board, move.to(), mover.opposite(), whiteKing, blackKing);
  }

  /** Returns whether the exchange started by {@code move} gains at least {@code threshold}. */
  public static boolean ge(Position position, Move move, int threshold) {
    return evaluate(position, move) >= threshold;
  }

  private static int seeCapture(int[] board, Square square, PieceColor side,
      int whiteKing, int blackKing) {
    int attackerIndex = leastValuableAttacker(board, square, side,
        side == PieceColor.WHITE ? whiteKing : blackKing);
    if (attackerIndex == -1) {
      return 0;
    }
    int squareIndex = Position.indexOf(square);
    int pieceOnSquare = board[squareIndex];
    int attacker = board[attackerIndex];
    int gain = PieceValues.of(Piece.type(pieceOnSquare));
    // A legal king recapture ends the exchange: the destination has no enemy attackers.
    if (Piece.type(attacker) == PieceType.KING) return gain;

    boolean promotes = Piece.type(attacker) == PieceType.PAWN
        && (square.getRank() == 0 || square.getRank() == 7);
    if (promotes) gain += PieceValues.QUEEN - PieceValues.PAWN;

    board[attackerIndex] = Piece.NONE;
    board[squareIndex] = promotes ? Piece.of(side, PieceType.QUEEN) : attacker;
    int value = Math.max(0, gain - seeCapture(board, square, side.opposite(), whiteKing, blackKing));
    board[attackerIndex] = attacker;
    board[squareIndex] = pieceOnSquare;
    return value;
  }

  private static int leastValuableAttacker(int[] board, Square square, PieceColor side, int kingSquare) {
    int bestIndex = -1;
    int bestValue = Integer.MAX_VALUE;
    for (int i = 0; i < 64; i++) {
      int piece = board[i];
      if (piece == Piece.NONE || Piece.color(piece) != side) {
        continue;
      }
      if (pieceAttacksSquare(board, i, square)) {
        // Material evaluation assigns zero to kings, but they must be considered last in SEE.
        int value = Piece.type(piece) == PieceType.KING ? Integer.MAX_VALUE - 1
            : PieceValues.of(Piece.type(piece));
        if (value < bestValue && legalRecapture(board, i, square, side, kingSquare)) {
          bestValue = value;
          bestIndex = i;
        }
      }
    }
    return bestIndex;
  }

  private static boolean legalRecapture(int[] board, int from, Square target,
      PieceColor side, int kingSquare) {
    int to = Position.indexOf(target);
    int attacker = board[from];
    int captured = board[to];
    int resultingKing = Piece.type(attacker) == PieceType.KING ? to : kingSquare;
    if (resultingKing < 0) return true; // Preserve support for material-only test positions.
    board[from] = Piece.NONE;
    board[to] = attacker;
    try {
      Square king = Position.squareOf(resultingKing);
      for (int i = 0; i < 64; i++) {
        if (board[i] != Piece.NONE && Piece.color(board[i]) != side
            && pieceAttacksSquare(board, i, king)) return false;
      }
      return true;
    } finally {
      board[from] = attacker;
      board[to] = captured;
    }
  }

  private static boolean pieceAttacksSquare(int[] board, int fromIndex, Square target) {
    int piece = board[fromIndex];
    int fromFile = fromIndex & 7;
    int fromRank = fromIndex >>> 3;
    int df = target.getFile() - fromFile;
    int dr = target.getRank() - fromRank;
    PieceColor color = Piece.color(piece);

    return switch (Piece.type(piece)) {
      case PAWN -> dr == (color == PieceColor.WHITE ? 1 : -1) && Math.abs(df) == 1;
      case KNIGHT ->
          (Math.abs(df) == 2 && Math.abs(dr) == 1) || (Math.abs(df) == 1 && Math.abs(dr) == 2);
      case BISHOP ->
          Math.abs(df) == Math.abs(dr) && df != 0 && clearPath(board, fromIndex, target);
      case ROOK ->
          (df == 0 || dr == 0) && (df != 0 || dr != 0) && clearPath(board, fromIndex, target);
      case QUEEN ->
          (Math.abs(df) == Math.abs(dr) || df == 0 || dr == 0)
              && (df != 0 || dr != 0)
              && clearPath(board, fromIndex, target);
      case KING -> Math.abs(df) <= 1 && Math.abs(dr) <= 1 && (df != 0 || dr != 0);
    };
  }

  private static boolean clearPath(int[] board, int fromIndex, Square target) {
    int fromFile = fromIndex & 7;
    int fromRank = fromIndex >>> 3;
    int stepFile = Integer.signum(target.getFile() - fromFile);
    int stepRank = Integer.signum(target.getRank() - fromRank);
    int file = fromFile + stepFile;
    int rank = fromRank + stepRank;
    while (file != target.getFile() || rank != target.getRank()) {
      if (board[rank * 8 + file] != Piece.NONE) {
        return false;
      }
      file += stepFile;
      rank += stepRank;
    }
    return true;
  }
}
