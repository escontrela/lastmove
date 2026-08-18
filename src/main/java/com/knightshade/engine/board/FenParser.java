package com.knightshade.engine.board;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import java.util.Objects;

/**
 * Parses a Forsyth-Edwards Notation string into a {@link Board}.
 *
 * <p>The engine treats FEN as its canonical position input (mirroring the "position fen" command of
 * an in-process UCI engine), so this parser is the single entry point from the adapter.
 */
public final class FenParser {

  private FenParser() {}

  public static Board parse(String fen) {
    String required = Objects.requireNonNull(fen, "fen must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("FEN must not be blank");
    }
    String[] fields = required.split("\\s+");
    if (fields.length < 4) {
      throw new IllegalArgumentException("FEN must contain at least 4 fields: " + fen);
    }

    Board board = Board.empty();
    parsePlacement(board, fields[0]);
    board.setSideToMove(parseSide(fields[1]));
    board.setCastlingRights(parseCastling(fields[2]));
    board.setEnPassantTarget(parseEnPassant(fields[3]));
    board.setHalfmoveClock(fields.length > 4 ? parseNonNegativeInt(fields[4]) : 0);
    board.setFullmoveNumber(fields.length > 5 ? parseFullmove(fields[5]) : 1);
    return board;
  }

  private static void parsePlacement(Board board, String placement) {
    String[] ranks = placement.split("/");
    if (ranks.length != 8) {
      throw new IllegalArgumentException("FEN placement must have 8 ranks: " + placement);
    }
    for (int rank = 0; rank < 8; rank++) {
      String row = ranks[7 - rank];
      int file = 0;
      for (int i = 0; i < row.length(); i++) {
        char symbol = row.charAt(i);
        if (file > 7) {
          throw new IllegalArgumentException("FEN rank has too many squares: " + row);
        }
        if (symbol >= '1' && symbol <= '8') {
          file += symbol - '0';
        } else {
          board.setPiece(Square.of(file, rank), pieceFromChar(symbol));
          file++;
        }
      }
      if (file != 8) {
        throw new IllegalArgumentException("FEN rank must span 8 squares: " + row);
      }
    }
  }

  private static int pieceFromChar(char symbol) {
    PieceColor color = Character.isUpperCase(symbol) ? PieceColor.WHITE : PieceColor.BLACK;
    PieceType type =
        switch (Character.toLowerCase(symbol)) {
          case 'p' -> PieceType.PAWN;
          case 'n' -> PieceType.KNIGHT;
          case 'b' -> PieceType.BISHOP;
          case 'r' -> PieceType.ROOK;
          case 'q' -> PieceType.QUEEN;
          case 'k' -> PieceType.KING;
          default -> throw new IllegalArgumentException("Unknown FEN piece symbol: " + symbol);
        };
    return Piece.of(color, type);
  }

  private static PieceColor parseSide(String field) {
    return switch (field) {
      case "w" -> PieceColor.WHITE;
      case "b" -> PieceColor.BLACK;
      default -> throw new IllegalArgumentException("Unknown FEN side to move: " + field);
    };
  }

  private static CastlingRights parseCastling(String field) {
    if ("-".equals(field)) {
      return CastlingRights.none();
    }
    return new CastlingRights(
        field.indexOf('K') >= 0,
        field.indexOf('Q') >= 0,
        field.indexOf('k') >= 0,
        field.indexOf('q') >= 0);
  }

  private static Square parseEnPassant(String field) {
    return "-".equals(field) ? null : Square.of(field);
  }

  private static int parseNonNegativeInt(String field) {
    int value = Integer.parseInt(field);
    if (value < 0) {
      throw new IllegalArgumentException("FEN counter must not be negative: " + field);
    }
    return value;
  }

  private static int parseFullmove(String field) {
    int value = parseNonNegativeInt(field);
    if (value < 1) {
      throw new IllegalArgumentException("FEN fullmove number must be at least one: " + field);
    }
    return value;
  }
}
