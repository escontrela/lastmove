package com.knightshade.engine.movegen;

import com.knightshade.engine.board.Board;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.MoveFlag;
import com.knightshade.engine.board.Piece;
import com.knightshade.engine.board.Position;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import java.util.ArrayList;
import java.util.List;

/** Pseudo-legal move generation using 64-bit attack and occupancy masks. */
public final class BitboardMoveGenerator extends LegalMoveGenerator {
  private static final long[] KNIGHTS = new long[64];
  private static final long[] KINGS = new long[64];
  private static final long[][] PAWNS = new long[2][64];
  private static final int[][] RAYS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };
  private static final PieceType[] PROMOTIONS = {
    PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
  };

  static {
    for (int from = 0; from < 64; from++) {
      int file = from & 7;
      int rank = from >>> 3;
      for (int df = -2; df <= 2; df++) {
        for (int dr = -2; dr <= 2; dr++) {
          int targetFile = file + df;
          int targetRank = rank + dr;
          if (!inside(targetFile, targetRank)) continue;
          if (Math.abs(df) * Math.abs(dr) == 2) KNIGHTS[from] |= bit(targetFile, targetRank);
          if (Math.max(Math.abs(df), Math.abs(dr)) == 1) KINGS[from] |= bit(targetFile, targetRank);
          if (Math.abs(df) == 1 && dr == 1) PAWNS[PieceColor.WHITE.ordinal()][from] |= bit(targetFile, targetRank);
          if (Math.abs(df) == 1 && dr == -1) PAWNS[PieceColor.BLACK.ordinal()][from] |= bit(targetFile, targetRank);
        }
      }
    }
  }

  @Override
  List<Move> generatePseudoLegal(Board board, boolean capturesOnly) {
    board.enableBitboards();
    PieceColor side = board.sideToMove();
    long own = board.occupancy(side);
    long enemy = board.occupancy(side.opposite());
    long empty = ~board.occupancy();
    List<Move> moves = new ArrayList<>();

    long pawns = board.pieceBitboard(Piece.of(side, PieceType.PAWN));
    while (pawns != 0) {
      int from = Long.numberOfTrailingZeros(pawns);
      pawns &= pawns - 1;
      long attacks = PAWNS[side.ordinal()][from];
      long captures = attacks & enemy;
      while (captures != 0) {
        int to = Long.numberOfTrailingZeros(captures);
        captures &= captures - 1;
        addPawnMove(board, moves, from, to, Piece.type(board.pieceAt(to)));
      }
      Square fromSquare = Position.squareOf(from);
      if (board.enPassantTarget() != null &&
          (attacks & (1L << Position.indexOf(board.enPassantTarget()))) != 0) {
        moves.add(new Move(fromSquare, board.enPassantTarget(), null, MoveFlag.EN_PASSANT,
            PieceType.PAWN));
      }
      int step = side == PieceColor.WHITE ? 8 : -8;
      int one = from + step;
      if (one < 0 || one >= 64 || (empty & (1L << one)) == 0) continue;
      if ((one >>> 3) == (side == PieceColor.WHITE ? 7 : 0)) {
        addPromotions(moves, from, one, null);
        continue;
      }
      if (capturesOnly) continue;
      moves.add(new Move(fromSquare, Position.squareOf(one), null, MoveFlag.NORMAL, null));
      int startRank = side == PieceColor.WHITE ? 1 : 6;
      int two = from + step * 2;
      if ((from >>> 3) == startRank && (empty & (1L << two)) != 0) {
        moves.add(new Move(fromSquare, Position.squareOf(two), null, MoveFlag.DOUBLE_PAWN_PUSH, null));
      }
    }

    addLeapers(moves, board.pieceBitboard(Piece.of(side, PieceType.KNIGHT)), KNIGHTS, board, own, enemy, capturesOnly);
    addLeapers(moves, board.pieceBitboard(Piece.of(side, PieceType.KING)), KINGS, board, own, enemy, capturesOnly);
    addSliders(moves, board.pieceBitboard(Piece.of(side, PieceType.BISHOP)), board, own, enemy, capturesOnly, true, false);
    addSliders(moves, board.pieceBitboard(Piece.of(side, PieceType.ROOK)), board, own, enemy, capturesOnly, false, true);
    addSliders(moves, board.pieceBitboard(Piece.of(side, PieceType.QUEEN)), board, own, enemy, capturesOnly, true, true);

    if (!capturesOnly) {
      addCastling(board, side, moves);
    }
    return moves;
  }

  @Override
  public boolean hasLegalMove(Board board) {
    PieceColor side = board.sideToMove();
    boolean inCheck = board.inCheck(side);
    long pinned = inCheck ? 0 : board.pinnedPieces(side);
    for (Move move : generatePseudoLegal(board, false)) {
      if (isLegal(board, side, inCheck, pinned, move)) return true;
    }
    return false;
  }

  private static void addCastling(Board board, PieceColor color, List<Move> moves) {
    int rank = color == PieceColor.WHITE ? 0 : 7;
    int kingFrom = rank * 8 + 4;
    PieceColor enemy = color.opposite();
    CastlingRights rights = board.castlingRights();
    boolean kingSide = color == PieceColor.WHITE ? rights.whiteKingSide() : rights.blackKingSide();
    boolean queenSide = color == PieceColor.WHITE ? rights.whiteQueenSide() : rights.blackQueenSide();
    if (kingSide && board.pieceAt(kingFrom + 1) == Piece.NONE
        && board.pieceAt(kingFrom + 2) == Piece.NONE
        && !isAttacked(board, kingFrom, enemy)
        && !isAttacked(board, kingFrom + 1, enemy)
        && !isAttacked(board, kingFrom + 2, enemy)) {
      moves.add(new Move(Position.squareOf(kingFrom), Position.squareOf(kingFrom + 2),
          null, MoveFlag.KING_CASTLE, null));
    }
    if (queenSide && board.pieceAt(kingFrom - 3) == Piece.NONE
        && board.pieceAt(kingFrom - 2) == Piece.NONE
        && board.pieceAt(kingFrom - 1) == Piece.NONE
        && !isAttacked(board, kingFrom, enemy)
        && !isAttacked(board, kingFrom - 2, enemy)
        && !isAttacked(board, kingFrom - 1, enemy)) {
      moves.add(new Move(Position.squareOf(kingFrom), Position.squareOf(kingFrom - 2),
          null, MoveFlag.QUEEN_CASTLE, null));
    }
  }

  @Override
  protected boolean isLegal(Board board, PieceColor side, boolean inCheck, long pinned, Move move) {
    boolean needsTest = inCheck || move.isEnPassant()
        || Piece.type(board.pieceAt(move.from())) == PieceType.KING
        || (pinned & (1L << Position.indexOf(move.from()))) != 0;
    if (!needsTest) return true;
    board.make(move);
    boolean legal = !isAttacked(board, Position.indexOf(board.kingSquare(side)), side.opposite());
    board.unmake();
    return legal;
  }

  private static boolean isAttacked(Board board, int target, PieceColor byColor) {
    int file = target & 7;
    int rank = target >>> 3;
    long targetBit = 1L << target;
    long knights = board.pieceBitboard(Piece.of(byColor, PieceType.KNIGHT));
    long kings = board.pieceBitboard(Piece.of(byColor, PieceType.KING));
    long pawns = board.pieceBitboard(Piece.of(byColor, PieceType.PAWN));
    if ((knights & KNIGHTS[target]) != 0 || (kings & KINGS[target]) != 0) return true;
    long pawnSources = byColor == PieceColor.WHITE
        ? ((targetBit >>> 7) & ~FILE_A_MASK()) | ((targetBit >>> 9) & ~FILE_H_MASK())
        : ((targetBit << 7) & ~FILE_H_MASK()) | ((targetBit << 9) & ~FILE_A_MASK());
    if ((pawns & pawnSources) != 0) return true;
    for (int direction = 0; direction < RAYS.length; direction++) {
      boolean diagonal = direction >= 4;
      int f = file + RAYS[direction][0];
      int r = rank + RAYS[direction][1];
      while (inside(f, r)) {
        int piece = board.pieceAt(r * 8 + f);
        if (piece != Piece.NONE) {
          if (Piece.color(piece) == byColor) {
            PieceType type = Piece.type(piece);
            if (type == PieceType.QUEEN || (diagonal && type == PieceType.BISHOP)
                || (!diagonal && type == PieceType.ROOK)) return true;
          }
          break;
        }
        f += RAYS[direction][0];
        r += RAYS[direction][1];
      }
    }
    return false;
  }

  private static long FILE_A_MASK() { return 0x0101010101010101L; }
  private static long FILE_H_MASK() { return 0x8080808080808080L; }

  private static void addPawnMove(Board board, List<Move> moves, int from, int to, PieceType captured) {
    if ((to >>> 3) == (Piece.color(board.pieceAt(from)) == PieceColor.WHITE ? 7 : 0)) {
      addPromotions(moves, from, to, captured);
    } else {
      moves.add(new Move(Position.squareOf(from), Position.squareOf(to), null, MoveFlag.NORMAL, captured));
    }
  }

  private static void addPromotions(List<Move> moves, int from, int to, PieceType captured) {
    for (PieceType promotion : PROMOTIONS) {
      moves.add(new Move(Position.squareOf(from), Position.squareOf(to), promotion, MoveFlag.NORMAL, captured));
    }
  }

  private static void addLeapers(List<Move> moves, long sources, long[] attacks,
      Board board, long own, long enemy, boolean capturesOnly) {
    while (sources != 0) {
      int from = Long.numberOfTrailingZeros(sources);
      sources &= sources - 1;
      long targets = attacks[from] & ~own & (capturesOnly ? enemy : -1L);
      addTargets(moves, from, targets, board);
    }
  }

  private static void addSliders(List<Move> moves, long sources, Board board,
      long own, long enemy, boolean capturesOnly, boolean diagonal, boolean orthogonal) {
    while (sources != 0) {
      int from = Long.numberOfTrailingZeros(sources);
      sources &= sources - 1;
      long attacks = 0;
      for (int direction = 0; direction < RAYS.length; direction++) {
        boolean isDiagonal = direction >= 4;
        if (isDiagonal ? !diagonal : !orthogonal) continue;
        int file = (from & 7) + RAYS[direction][0];
        int rank = (from >>> 3) + RAYS[direction][1];
        while (inside(file, rank)) {
          long target = bit(file, rank);
          attacks |= target;
          if (board.pieceAt(rank * 8 + file) != Piece.NONE) break;
          file += RAYS[direction][0];
          rank += RAYS[direction][1];
        }
      }
      addTargets(moves, from, attacks & ~own & (capturesOnly ? enemy : -1L), board);
    }
  }

  private static void addTargets(List<Move> moves, int from, long targets, Board board) {
    while (targets != 0) {
      int to = Long.numberOfTrailingZeros(targets);
      targets &= targets - 1;
      int targetPiece = board.pieceAt(to);
      PieceType captured = targetPiece == Piece.NONE ? null : Piece.type(targetPiece);
      moves.add(new Move(Position.squareOf(from), Position.squareOf(to), null, MoveFlag.NORMAL, captured));
    }
  }

  private static long bit(int file, int rank) { return 1L << (rank * 8 + file); }
  private static boolean inside(int file, int rank) { return file >= 0 && file < 8 && rank >= 0 && rank < 8; }
}
