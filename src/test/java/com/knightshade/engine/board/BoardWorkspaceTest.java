package com.knightshade.engine.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.knightshade.engine.movegen.LegalMoveGenerator;
import org.junit.jupiter.api.Test;

class BoardWorkspaceTest {
  @Test
  void copyHasIndependentPiecesKingCacheAndUndoHistory() {
    Board original = FenParser.parse("r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 17 32");
    String fen = original.toFen();
    Board copy = original.copy();
    assertEquals(fen, copy.toFen());
    assertEquals(original.zobristKey(), copy.zobristKey());
    for (Move move : new LegalMoveGenerator().generate(copy)) {
      copy.make(move);
      assertEquals(fen, original.toFen());
      assertEquals(Square.of("e1"), original.kingSquare(PieceColor.WHITE));
      copy.unmake();
      assertEquals(fen, copy.toFen());
      assertEquals(original.zobristKey(), copy.zobristKey());
    }
  }

  @Test
  void reusableUndoFramesGrowAndRestoreMixedNullAndOrdinaryMoves() {
    String fen = "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 17 32";
    Board board = FenParser.parse(fen);
    long key = board.zobristKey();
    for (int round = 0; round < 3; round++) {
      for (int ply = 0; ply < 160; ply++) {
        board.makeNullMove();
      }
      for (Move move : new LegalMoveGenerator().generate(board)) {
        String before = board.toFen();
        board.make(move);
        board.unmake();
        assertEquals(before, board.toFen());
      }
      for (int ply = 0; ply < 160; ply++) {
        board.unmakeNullMove();
      }
      assertEquals(fen, board.toFen());
      assertEquals(key, board.zobristKey());
      assertEquals(Square.of("e1"), board.kingSquare(PieceColor.WHITE));
      assertEquals(Square.of("e8"), board.kingSquare(PieceColor.BLACK));
    }
  }
}
