package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.service.FenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PositionEditorServiceTest {
  @Test
  void onlyGeneratesFenForAValidAuthoredPosition() {
    ChessRulesEngine rules = Mockito.mock(ChessRulesEngine.class);
    when(rules.startingPosition()).thenReturn(validPosition());
    PositionEditorService editor = new PositionEditorService(rules, new FenService());

    assertFalse(editor.state().valid());
    editor.reset();
    assertTrue(editor.state().valid());
    assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", editor.fen().getValue());

    editor.remove(Square.of("e8"));
    assertFalse(editor.state().valid());
  }

  @Test
  void rejectsMetadataThatCannotDescribeTheAuthoredBoard() {
    ChessRulesEngine rules = Mockito.mock(ChessRulesEngine.class);
    when(rules.startingPosition()).thenReturn(validPosition());
    PositionEditorService editor = new PositionEditorService(rules, new FenService());

    editor.reset();
    editor.configure(PieceColor.WHITE, new CastlingRights(true, false, false, false), Optional.empty(), 0, 1);

    assertFalse(editor.state().valid());
    assertTrue(editor.state().validationMessage().orElseThrow().contains("rook on h1"));
  }

  @Test
  void offersEnPassantOnlyWhenAnAdjacentPawnCanCapture() {
    PositionEditorService editor =
        new PositionEditorService(Mockito.mock(ChessRulesEngine.class), new FenService());
    editor.place(Square.of("d5"), PieceType.PAWN, PieceColor.BLACK);

    assertTrue(editor.enPassantTargets(PieceColor.WHITE).isEmpty());

    editor.place(Square.of("e5"), PieceType.PAWN, PieceColor.WHITE);

    assertIterableEquals(List.of(Square.of("d6")), editor.enPassantTargets(PieceColor.WHITE));
  }

  private static PositionSnapshot validPosition() {
    return new PositionSnapshot(
        List.of(
            new PositionPiece(Square.of("e1"), PieceType.KING, PieceColor.WHITE),
            new PositionPiece(Square.of("e8"), PieceType.KING, PieceColor.BLACK)),
        PieceColor.WHITE, CastlingRights.none(), Optional.empty(), 0, 1, Optional.empty(), false, false, false);
  }
}
