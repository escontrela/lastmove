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

/**
 * Generates pseudo-legal moves for the side to move and filters them to the legal subset.
 *
 * <p>Legality is established by making each pseudo-legal move and rejecting it when the moving
 * side's own king is left in check. Castling additionally verifies that the king neither crosses
 * nor lands on an attacked square, which the final make/unmake check alone cannot catch.
 */
public final class LegalMoveGenerator implements MoveGenerator {

  private static final int[][] KNIGHT_OFFSETS = {
    {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
  };

  private static final int[][] KING_OFFSETS = {
    {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
  };

  private static final int[][] ROOK_DIRECTIONS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}
  };

  private static final int[][] BISHOP_DIRECTIONS = {
    {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private static final List<PieceType> PROMOTION_PIECES =
      List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT);

  @Override
  public List<Move> generate(Board board) {
    return filterLegal(board, board.sideToMove(), generatePseudoLegal(board));
  }

  @Override
  public List<Move> generateCaptures(Board board) {
    List<Move> captures = new ArrayList<>();
    for (Move move : generatePseudoLegal(board)) {
      if (move.isCapture() || move.isPromotion()) {
        captures.add(move);
      }
    }
    return filterLegal(board, board.sideToMove(), captures);
  }

  private List<Move> generatePseudoLegal(Board board) {
    List<Move> pseudoLegal = new ArrayList<>();
    PieceColor side = board.sideToMove();
    for (int index = 0; index < 64; index++) {
      int piece = board.pieceAt(index);
      if (piece == Piece.NONE || Piece.color(piece) != side) {
        continue;
      }
      Square from = Position.squareOf(index);
      switch (Piece.type(piece)) {
        case PAWN -> generatePawnMoves(board, from, piece, pseudoLegal);
        case KNIGHT -> generateLeaperMoves(board, from, piece, KNIGHT_OFFSETS, pseudoLegal);
        case BISHOP -> generateSliderMoves(board, from, piece, BISHOP_DIRECTIONS, pseudoLegal);
        case ROOK -> generateSliderMoves(board, from, piece, ROOK_DIRECTIONS, pseudoLegal);
        case QUEEN -> {
          generateSliderMoves(board, from, piece, BISHOP_DIRECTIONS, pseudoLegal);
          generateSliderMoves(board, from, piece, ROOK_DIRECTIONS, pseudoLegal);
        }
        case KING -> generateKingMoves(board, from, piece, pseudoLegal);
      }
    }
    return pseudoLegal;
  }

  private List<Move> filterLegal(Board board, PieceColor side, List<Move> pseudoLegal) {
    List<Move> legal = new ArrayList<>();
    for (Move move : pseudoLegal) {
      board.make(move);
      boolean leavesKingSafe = !board.isSquareAttacked(board.kingSquare(side), side.opposite());
      board.unmake();
      if (leavesKingSafe) {
        legal.add(move);
      }
    }
    return legal;
  }

  private void generatePawnMoves(
      Board board, Square from, int piece, List<Move> moves) {
    PieceColor color = Piece.color(piece);
    int direction = color == PieceColor.WHITE ? 1 : -1;
    int file = from.getFile();
    int rank = from.getRank();
    int startRank = color == PieceColor.WHITE ? 1 : 6;
    int promotionRank = color == PieceColor.WHITE ? 7 : 0;
    int nextRank = rank + direction;

    if (nextRank < 0 || nextRank > 7) {
      return;
    }

    Square oneAhead = Square.of(file, nextRank);
    if (board.pieceAt(oneAhead) == Piece.NONE) {
      if (nextRank == promotionRank) {
        addPromotions(from, oneAhead, null, moves);
      } else {
        moves.add(new Move(from, oneAhead, null, MoveFlag.NORMAL, null));
        if (rank == startRank) {
          Square twoAhead = Square.of(file, rank + 2 * direction);
          if (board.pieceAt(twoAhead) == Piece.NONE) {
            moves.add(new Move(from, twoAhead, null, MoveFlag.DOUBLE_PAWN_PUSH, null));
          }
        }
      }
    }

    for (int fileDelta : new int[] {-1, 1}) {
      int targetFile = file + fileDelta;
      if (targetFile < 0 || targetFile > 7) {
        continue;
      }
      Square target = Square.of(targetFile, nextRank);
      int targetPiece = board.pieceAt(target);
      if (targetPiece != Piece.NONE && Piece.color(targetPiece) != color) {
        if (nextRank == promotionRank) {
          addPromotions(from, target, Piece.type(targetPiece), moves);
        } else {
          moves.add(new Move(from, target, null, MoveFlag.NORMAL, Piece.type(targetPiece)));
        }
      }
      Square enPassant = board.enPassantTarget();
      if (enPassant != null && enPassant.equals(target)) {
        moves.add(new Move(from, target, null, MoveFlag.EN_PASSANT, PieceType.PAWN));
      }
    }
  }

  private void addPromotions(
      Square from, Square to, PieceType captured, List<Move> moves) {
    for (PieceType promotion : PROMOTION_PIECES) {
      moves.add(new Move(from, to, promotion, MoveFlag.NORMAL, captured));
    }
  }

  private void generateLeaperMoves(
      Board board, Square from, int piece, int[][] offsets, List<Move> moves) {
    PieceColor color = Piece.color(piece);
    for (int[] offset : offsets) {
      int targetFile = from.getFile() + offset[0];
      int targetRank = from.getRank() + offset[1];
      if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
        continue;
      }
      Square target = Square.of(targetFile, targetRank);
      int targetPiece = board.pieceAt(target);
      if (targetPiece == Piece.NONE) {
        moves.add(new Move(from, target, null, MoveFlag.NORMAL, null));
      } else if (Piece.color(targetPiece) != color) {
        moves.add(new Move(from, target, null, MoveFlag.NORMAL, Piece.type(targetPiece)));
      }
    }
  }

  private void generateSliderMoves(
      Board board, Square from, int piece, int[][] directions, List<Move> moves) {
    PieceColor color = Piece.color(piece);
    for (int[] direction : directions) {
      int targetFile = from.getFile() + direction[0];
      int targetRank = from.getRank() + direction[1];
      while (targetFile >= 0 && targetFile < 8 && targetRank >= 0 && targetRank < 8) {
        Square target = Square.of(targetFile, targetRank);
        int targetPiece = board.pieceAt(target);
        if (targetPiece == Piece.NONE) {
          moves.add(new Move(from, target, null, MoveFlag.NORMAL, null));
        } else {
          if (Piece.color(targetPiece) != color) {
            moves.add(new Move(from, target, null, MoveFlag.NORMAL, Piece.type(targetPiece)));
          }
          break;
        }
        targetFile += direction[0];
        targetRank += direction[1];
      }
    }
  }

  private void generateKingMoves(Board board, Square from, int piece, List<Move> moves) {
    generateLeaperMoves(board, from, piece, KING_OFFSETS, moves);
    generateCastling(board, Piece.color(piece), moves);
  }

  private void generateCastling(Board board, PieceColor color, List<Move> moves) {
    int rank = color == PieceColor.WHITE ? 0 : 7;
    Square kingFrom = Square.of(4, rank);
    PieceColor enemy = color.opposite();
    CastlingRights rights = board.castlingRights();
    boolean kingSide = color == PieceColor.WHITE ? rights.whiteKingSide() : rights.blackKingSide();
    boolean queenSide =
        color == PieceColor.WHITE ? rights.whiteQueenSide() : rights.blackQueenSide();

    if (kingSide) {
      Square rookDestination = Square.of(5, rank);
      Square kingDestination = Square.of(6, rank);
      if (board.pieceAt(rookDestination) == Piece.NONE
          && board.pieceAt(kingDestination) == Piece.NONE
          && !board.isSquareAttacked(kingFrom, enemy)
          && !board.isSquareAttacked(rookDestination, enemy)
          && !board.isSquareAttacked(kingDestination, enemy)) {
        moves.add(new Move(kingFrom, kingDestination, null, MoveFlag.KING_CASTLE, null));
      }
    }
    if (queenSide) {
      Square rookPath = Square.of(1, rank);
      Square kingPath = Square.of(2, rank);
      Square rookDestination = Square.of(3, rank);
      if (board.pieceAt(rookPath) == Piece.NONE
          && board.pieceAt(kingPath) == Piece.NONE
          && board.pieceAt(rookDestination) == Piece.NONE
          && !board.isSquareAttacked(kingFrom, enemy)
          && !board.isSquareAttacked(kingPath, enemy)
          && !board.isSquareAttacked(rookDestination, enemy)) {
        moves.add(new Move(kingFrom, kingPath, null, MoveFlag.QUEEN_CASTLE, null));
      }
    }
  }
}
