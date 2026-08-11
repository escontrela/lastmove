package com.escontrela.lastmove.domain.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameSessionTest {

  @Test
  void apply_keepsExistingLineAndCreatesVariationAfterGoingBack() {
    GameSession session = new GameSession(SessionId.random(), GameSessionOrigin.INITIAL_POSITION, initial());

    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    Ply first = session.currentPly().orElseThrow();
    session.apply(accepted("e7", "e5", "e5", initial()));
    Ply mainLineSecond = session.currentPly().orElseThrow();

    assertTrue(session.previous());
    session.apply(accepted("c7", "c5", "c5", initial()));
    Ply variationSecond = session.currentPly().orElseThrow();

    assertEquals(List.of(first, variationSecond), session.moveHistory());
    assertEquals(2, first.variations().size());
    assertTrue(session.select(mainLineSecond));
    assertEquals(List.of(first, mainLineSecond), session.moveHistory());
  }

  @Test
  void rejectedResult_preservesPositionAndHistory() {
    GameSession session = new GameSession(SessionId.random(), GameSessionOrigin.FEN, initial());

    session.apply(MoveExecutionResult.rejected(initial(), "Illegal move"));

    assertEquals(initial(), session.currentPosition());
    assertTrue(session.moveHistory().isEmpty());
    assertFalse(session.previous());
  }

  @Test
  void notationLine_includesMovesAheadOfTheCurrentCursor() {
    GameSession session = new GameSession(SessionId.random(), GameSessionOrigin.INITIAL_POSITION, initial());
    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    session.apply(accepted("e7", "e5", "e5", initial()));

    assertTrue(session.previous());

    assertEquals(2, session.notationLine().size());
    assertEquals("e5", session.notationLine().get(1).move().san().getValue());
  }

  @Test
  void sessionState_isDerivedFromCurrentSnapshot() {
    PositionSnapshot snapshot = new PositionSnapshot(
        List.of(), PieceColor.BLACK, CastlingRights.initial(), Optional.of(Square.of("e3")), 7, 12,
        Optional.empty(), true, false, false);
    GameSession session = new GameSession(SessionId.random(), GameSessionOrigin.FEN, snapshot);

    assertEquals(PieceColor.BLACK, session.gameState().whoseTurn());
    assertEquals(7, session.gameState().halfmoveClock());
    assertEquals(12, session.gameState().fullmoveNumber());
    assertEquals(Optional.of(Square.of("e3")), session.gameState().enPassantTarget());
  }

  private static MoveExecutionResult accepted(
      String from, String to, String san, PositionSnapshot snapshot) {
    return MoveExecutionResult.accepted(
        snapshot,
        new MoveDescriptor(
            Square.of(from), Square.of(to), SanMove.of(san), false, false, false, Optional.empty()));
  }

  private static PositionSnapshot initial() {
    return new PositionSnapshot(
        List.of(new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE)),
        PieceColor.WHITE,
        CastlingRights.initial(),
        Optional.empty(),
        0,
        1,
        Optional.empty(),
        false,
        false,
        false);
  }

  private static PositionSnapshot blackToMove() {
    return new PositionSnapshot(
        List.of(new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE)),
        PieceColor.BLACK,
        CastlingRights.initial(),
        Optional.of(Square.of("e3")),
        0,
        1,
        Optional.empty(),
        false,
        false,
        false);
  }
}
