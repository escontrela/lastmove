package com.escontrela.lastmove.domain.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class AnalysisDocumentTest {

  private final AnalysisDocumentFactory factory = new AnalysisDocumentFactory();

  @Test
  void behavesLikeAnAnalysisSessionAcrossVariationsAndNavigation() {
    AnalysisDocument document = newDocument();

    document.apply(accepted("e2", "e4", "e4", blackToMove()));
    AnalysisNode first = document.currentNode().orElseThrow();
    document.apply(accepted("e7", "e5", "e5", initial()));
    AnalysisNode mainLineSecond = document.currentNode().orElseThrow();

    assertTrue(document.previous());
    document.apply(accepted("c7", "c5", "c5", initial()));
    AnalysisNode alternativeSecond = document.currentNode().orElseThrow();

    assertEquals(2, document.continuations(first.id()).size());
    assertEquals(List.of("e5", "c5"), nodeSans(document.continuations(first.id())));
    assertEquals(List.of("e4", "c5"), plySans(document.currentLine()));

    assertTrue(document.previous());
    assertTrue(document.next());
    assertEquals("c5", document.currentPly().orElseThrow().move().san().getValue());

    assertTrue(document.select(mainLineSecond.id()));
    assertEquals(List.of("e4", "e5"), plySans(document.currentLine()));
    assertFalse(mainLineSecond.id().equals(alternativeSecond.id()));
  }

  @Test
  void deepCopyKeepsVariationsButSharesNoMutableState() {
    AnalysisDocument source = newDocument();
    source.apply(accepted("e2", "e4", "e4", blackToMove()));
    AnalysisNode e4 = source.currentNode().orElseThrow();
    source.apply(accepted("e7", "e5", "e5", initial()));
    source.previous();
    source.apply(accepted("c7", "c5", "c5", initial()));
    source.select(e4.id());

    AnalysisDocument copy = factory.copyOf(source);

    AnalysisNode copiedCursor = copy.currentNode().orElseThrow();
    assertNotEquals(e4.id(), copiedCursor.id());
    assertEquals(List.of("e5", "c5"), nodeSans(copy.continuations(copiedCursor.id())));
    assertEquals(List.of("e4", "e5"), plySans(copy.notationLine()));

    copy.apply(accepted("d2", "d4", "d4", initial()));

    assertEquals(2, source.continuations(e4.id()).size());
    assertEquals(List.of("e4"), plySans(source.currentLine()));
    assertEquals(3, copy.continuations(copiedCursor.id()).size());
  }

  private AnalysisDocument newDocument() {
    return factory.fromPosition(initial(), Optional.empty());
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
