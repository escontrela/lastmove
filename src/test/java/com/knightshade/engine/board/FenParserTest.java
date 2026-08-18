package com.knightshade.engine.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

class FenParserTest {

  private static final String STARTING_FEN =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @Test
  void roundTripsTheStartingPosition() {
    assertEquals(STARTING_FEN, FenParser.parse(STARTING_FEN).toFen());
  }

  @Test
  void roundTripsCastlingEnPassantAndCounters() {
    String fen = "rnbqkbnr/ppp2ppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3";
    assertEquals(fen, FenParser.parse(fen).toFen());
  }

  @Test
  void parsesPiecesSideAndState() {
    Board board = FenParser.parse(STARTING_FEN);

    assertEquals(PieceColor.WHITE, board.sideToMove());
    assertEquals(PieceType.ROOK, Piece.type(board.pieceAt(Square.of("a1"))));
    assertEquals(PieceColor.WHITE, Piece.color(board.pieceAt(Square.of("a1"))));
    assertEquals(PieceType.QUEEN, Piece.type(board.pieceAt(Square.of("d8"))));
    assertEquals(PieceColor.BLACK, Piece.color(board.pieceAt(Square.of("d8"))));
    assertEquals(0, board.halfmoveClock());
    assertEquals(1, board.fullmoveNumber());
  }

  @Test
  void defaultsMissingMoveCounters() {
    Board board = FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -");

    assertEquals(0, board.halfmoveClock());
    assertEquals(1, board.fullmoveNumber());
  }

  @Test
  void makeAndUnmakeRestorePositionExactly() {
    Board board = FenParser.parse(STARTING_FEN);
    String before = board.toFen();

    Move move = new Move(Square.of("e2"), Square.of("e4"), null, MoveFlag.DOUBLE_PAWN_PUSH, null);
    board.make(move);
    board.unmake();

    assertEquals(before, board.toFen());
  }
}
