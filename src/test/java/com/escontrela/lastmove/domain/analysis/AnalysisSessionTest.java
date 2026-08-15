package com.escontrela.lastmove.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnalysisSessionTest {

  @Test
  void createsVariationWithoutChangingTheExistingContinuation() {
    AnalysisSession session = newSession();

    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    AnalysisNode first = session.currentNode().orElseThrow();
    session.apply(accepted("e7", "e5", "e5", initial()));
    AnalysisNode mainLineSecond = session.currentNode().orElseThrow();

    assertTrue(session.previous());
    session.apply(accepted("c7", "c5", "c5", initial()));
    AnalysisNode alternativeSecond = session.currentNode().orElseThrow();

    assertEquals(2, session.continuations(first.id()).size());
    assertEquals(List.of("e5", "c5"), nodeSans(session.continuations(first.id())));
    assertEquals(List.of("e4", "c5"), plySans(session.currentLine()));

    assertTrue(session.previous());
    assertTrue(session.next());
    assertEquals("c5", session.currentPly().orElseThrow().move().san().getValue());

    assertTrue(session.select(mainLineSecond.id()));
    assertEquals(List.of("e4", "e5"), plySans(session.currentLine()));
    assertFalse(mainLineSecond.id().equals(alternativeSecond.id()));
  }

  @Test
  void selectsAnExistingContinuationInsteadOfDuplicatingIt() {
    AnalysisSession session = newSession();
    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    AnalysisNode existing = session.currentNode().orElseThrow();

    session.first();
    session.apply(accepted("e2", "e4", "e4", blackToMove()));

    assertEquals(existing.id(), session.currentNode().orElseThrow().id());
    assertEquals(1, session.rootVariations().size());
  }

  @Test
  void navigationKeepsThePreferredLineVisibleAheadOfTheCursor() {
    AnalysisSession session = newSession();
    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    session.apply(accepted("e7", "e5", "e5", initial()));

    assertTrue(session.previous());

    assertEquals(List.of("e4"), plySans(session.currentLine()));
    assertEquals(List.of("e4", "e5"), plySans(session.notationLine()));
    assertEquals(List.of("e4", "e5"), nodeSans(session.notationNodes()));
    assertTrue(session.next());
    assertEquals("e5", session.currentPly().orElseThrow().move().san().getValue());
  }

  @Test
  void firstAndLastReachThePreferredLineBoundaries() {
    AnalysisSession session = newSession();
    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    session.apply(accepted("e7", "e5", "e5", initial()));

    session.first();
    assertTrue(session.currentPly().isEmpty());
    session.last();

    assertEquals("e5", session.currentPly().orElseThrow().move().san().getValue());
  }

  @Test
  void renamesTheSessionWithoutChangingItsTreeOrCursor() {
    AnalysisSession session = newSession();
    session.apply(accepted("e2", "e4", "e4", blackToMove()));
    AnalysisNode selected = session.currentNode().orElseThrow();

    session.rename("  Sicilian ideas  ");

    assertEquals("Sicilian ideas", session.title());
    assertEquals(selected.id(), session.currentNode().orElseThrow().id());
    assertEquals(List.of("e4"), plySans(session.notationLine()));
    assertThrows(IllegalArgumentException.class, () -> session.rename("   "));
  }

  private AnalysisSession newSession() {
    return new AnalysisSession(
        AnalysisSessionId.random(), "Test", AnalysisOrigin.INITIAL_POSITION, initial());
  }

  private List<String> nodeSans(List<AnalysisNode> nodes) {
    return nodes.stream().map(node -> node.ply().move().san().getValue()).toList();
  }

  private List<String> plySans(List<Ply> plies) {
    return plies.stream().map(ply -> ply.move().san().getValue()).toList();
  }

  private MoveExecutionResult accepted(
      String from, String to, String san, PositionSnapshot snapshot) {
    return MoveExecutionResult.accepted(
        snapshot,
        new MoveDescriptor(
            Square.of(from),
            Square.of(to),
            SanMove.of(san),
            false,
            false,
            false,
            Optional.empty()));
  }

  private PositionSnapshot initial() {
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

  private PositionSnapshot blackToMove() {
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
