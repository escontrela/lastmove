package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoMoveValidator;
import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameMoveServiceTest {

  private final GameMoveService service = new GameMoveService(new ChesspressoMoveValidator());

  @Test
  void attemptMove_appliesLegalMoveAndReturnsResultingFen() {
    MoveExecutionResult result = attempt("e2", "e4");

    assertTrue(result.accepted());
    assertEquals(32, result.newSnapshot().pieces().size());
    assertEquals("e4", result.move().orElseThrow().to().toAlgebraic());
    assertEquals("e4", result.move().orElseThrow().san().getValue());
    assertEquals(Square.of("e3"), result.newSnapshot().enPassantTarget().orElseThrow());
    assertEquals(1, result.newSnapshot().fullmoveNumber());
  }

  @Test
  void attemptMove_rejectsIllegalMoveAndLeavesPositionUnchanged() {
    MoveExecutionResult rejected = attempt("e2", "e5");

    assertFalse(rejected.accepted());
    assertEquals(
        32, rejected.newSnapshot().pieces().size());
    assertTrue(rejected.rejectionReason().orElseThrow().contains("Illegal move"));
  }

  @Test
  void validate_doesNotMutateTheSuppliedPosition() {
    PositionSnapshot startingPosition = service.startingPosition();

    MoveExecutionResult result = attempt("e2", "e4");

    assertTrue(result.accepted());
    assertEquals(32, startingPosition.pieces().size());
    assertEquals(
        "e2",
        startingPosition.pieces().stream()
            .filter(piece -> piece.square().equals(Square.of("e2")))
            .findFirst()
            .orElseThrow()
            .square()
            .toAlgebraic());
  }

  @Test
  void attemptMove_reportsCaptureCastlingPromotionAndEnPassant() {
    MoveExecutionResult castle =
        attempt("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1", "g1", Optional.empty());
    MoveExecutionResult promotion =
        attempt("8/P7/8/8/8/8/8/k6K w - - 0 1", "a7", "a8", Optional.of(PieceType.QUEEN));
    MoveExecutionResult enPassant =
        attempt("7k/8/8/3pP3/8/8/8/K7 w - d6 0 1", "e5", "d6", Optional.empty());

    assertTrue(castle.accepted());
    assertTrue(castle.move().orElseThrow().castling());
    assertTrue(promotion.accepted());
    assertEquals(PieceType.QUEEN, promotion.move().orElseThrow().promotion().orElseThrow());
    assertTrue(enPassant.accepted());
    assertTrue(enPassant.move().orElseThrow().enPassant());
    assertEquals("d5", enPassant.capturedPiece().orElseThrow().square().toAlgebraic());
  }

  @Test
  void attemptMove_reportsMateAndStalemate() {
    MoveExecutionResult mate =
        attempt("7k/8/6QK/8/8/8/8/8 w - - 0 1", "g6", "g7", Optional.empty());
    MoveExecutionResult stalemate =
        attempt("k7/8/1QK5/8/8/8/8/8 w - - 0 1", "b6", "c7", Optional.empty());

    assertTrue(mate.accepted());
    assertTrue(mate.mate());
    assertTrue(stalemate.accepted());
    assertTrue(stalemate.stalemate());
  }

  private MoveExecutionResult attempt(String from, String to) {
    return service.validate(
        service.startingPosition(), new MoveCommand(Square.of(from), Square.of(to), Optional.empty()));
  }

  private MoveExecutionResult attempt(
      String fen, String from, String to, Optional<PieceType> promotion) {
    return service.validate(
        service.snapshotFor(Fen.of(fen)), new MoveCommand(Square.of(from), Square.of(to), promotion));
  }
}
