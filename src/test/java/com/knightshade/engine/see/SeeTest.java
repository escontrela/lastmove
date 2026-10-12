package com.knightshade.engine.see;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.board.FenParser;
import com.knightshade.engine.board.Move;
import com.knightshade.engine.board.MoveFlag;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import org.junit.jupiter.api.Test;

class SeeTest {

  @Test
  void doesNotCountAKingRecaptureOntoAnAttackedSquare() {
    var board = FenParser.parse("8/8/4k3/3p4/5N2/8/8/K2R4 w - - 0 1");
    Move move = new Move(Square.of("f4"), Square.of("d5"), null, MoveFlag.NORMAL, PieceType.PAWN);
    assertEquals(100, See.evaluate(board, move));
  }

  @Test
  void doesNotCountARecaptureThatExposesTheDefendersKing() {
    var board = FenParser.parse("4k3/8/8/3pr3/5N2/8/8/K3R3 w - - 0 1");
    Move move = new Move(Square.of("f4"), Square.of("d5"), null, MoveFlag.NORMAL, PieceType.PAWN);
    assertEquals(100, See.evaluate(board, move));
  }

  @Test
  void stillCountsALegalKingRecapture() {
    var board = FenParser.parse("8/8/4k3/3p4/5N2/8/8/K7 w - - 0 1");
    Move move = new Move(Square.of("f4"), Square.of("d5"), null, MoveFlag.NORMAL, PieceType.PAWN);
    assertEquals(-220, See.evaluate(board, move));
  }

  @Test
  void aPinnedDefenderCanRecaptureAlongThePinLine() {
    var board = FenParser.parse("4k3/8/4r3/4p3/8/2Q5/8/K3R3 w - - 0 1");
    Move move = new Move(Square.of("c3"), Square.of("e5"), null, MoveFlag.NORMAL, PieceType.PAWN);
    // ...Rxe5 stays on the pin line, then Rxe5: White loses a queen for rook and pawn.
    assertEquals(-300, See.evaluate(board, move));
  }

  @Test
  void includesPromotionDuringARecapture() {
    var board = FenParser.parse("7K/8/7k/8/8/8/pR6/1q6 w - - 0 1");
    String original = board.toFen();
    Move move = new Move(Square.of("b2"), Square.of("b1"), null, MoveFlag.NORMAL, PieceType.QUEEN);
    assertEquals(-400, See.evaluate(board, move));
    assertEquals(original, board.toFen());
  }

  @Test
  void winningCaptureGainsTheCapturedPieceValue() {
    var board = FenParser.parse("7k/8/8/3q4/4P3/8/8/7K w - - 0 1");
    Move move = new Move(Square.of("e4"), Square.of("d5"), null, MoveFlag.NORMAL, PieceType.QUEEN);

    assertEquals(900, See.evaluate(board, move));
    assertTrue(See.ge(board, move, 0));
  }

  @Test
  void losingCaptureIsNegative() {
    var board = FenParser.parse("4r2k/8/8/4p3/8/8/8/4R2K w - - 0 1");
    Move move = new Move(Square.of("e1"), Square.of("e5"), null, MoveFlag.NORMAL, PieceType.PAWN);

    assertEquals(-400, See.evaluate(board, move));
    assertFalse(See.ge(board, move, 0));
  }

  @Test
  void equalPawnExchangeIsNeutral() {
    var board = FenParser.parse("7k/8/2p5/3p4/4P3/8/8/7K w - - 0 1");
    Move move = new Move(Square.of("e4"), Square.of("d5"), null, MoveFlag.NORMAL, PieceType.PAWN);

    assertEquals(0, See.evaluate(board, move));
  }

  @Test
  void includesTheMaterialCreatedByPromotion() {
    var board = FenParser.parse("7k/P7/8/8/8/8/8/7K w - - 0 1");
    Move move =
        new Move(Square.of("a7"), Square.of("a8"), PieceType.QUEEN, MoveFlag.NORMAL, null);

    assertEquals(800, See.evaluate(board, move));
    assertTrue(See.ge(board, move, 0));
  }
}
