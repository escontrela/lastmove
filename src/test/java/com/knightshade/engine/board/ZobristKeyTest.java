package com.knightshade.engine.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.escontrela.lastmove.domain.common.Square;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import org.junit.jupiter.api.Test;

class ZobristKeyTest {

  private static final String STARTING_FEN =
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  @Test
  void identicalPositionsHaveIdenticalKeys() {
    Board first = FenParser.parse(STARTING_FEN);
    Board second = FenParser.parse(STARTING_FEN);

    assertEquals(first.zobristKey(), second.zobristKey());
  }

  @Test
  void makeAndUnmakeRestoreTheKey() {
    Board board = FenParser.parse(STARTING_FEN);
    long before = board.zobristKey();

    Move move = new Move(Square.of("e2"), Square.of("e4"), null, MoveFlag.DOUBLE_PAWN_PUSH, null);
    board.make(move);
    assertNotEquals(before, board.zobristKey());
    board.unmake();

    assertEquals(before, board.zobristKey());
  }

  @Test
  void keyMatchesAFromScratchRecomputationAfterMoves() {
    Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
    var legal = new LegalMoveGenerator().generate(board);

    for (com.knightshade.engine.board.Move move : legal) {
      board.make(move);
      long incremental = board.zobristKey();
      long recomputed = recompute(board);
      assertEquals(recomputed, incremental, "key mismatch after " + move);
      board.unmake();
    }
  }

  private long recompute(Board board) {
    long key = 0;
    for (int index = 0; index < 64; index++) {
      int piece = board.pieceAt(index);
      if (piece != Piece.NONE) {
        key ^= Zobrist.piece(piece, index);
      }
    }
    if (board.sideToMove() == com.escontrela.lastmove.domain.common.PieceColor.BLACK) {
      key ^= Zobrist.sideToMove();
    }
    key ^= Zobrist.castling(board.castlingRights());
    key ^= Zobrist.enPassant(board.enPassantTarget());
    return key;
  }
}
