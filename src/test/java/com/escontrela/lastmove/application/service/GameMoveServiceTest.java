package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoMoveValidator;
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

  private MoveExecutionResult attempt(String from, String to) {
    return service.validate(
        service.startingPosition(), new MoveCommand(Square.of(from), Square.of(to), Optional.empty()));
  }
}
