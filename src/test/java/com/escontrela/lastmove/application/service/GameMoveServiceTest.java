package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.dto.MoveRequest;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoGameSessionRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameMoveServiceTest {

  private final GameMoveService service = new GameMoveService(new ChesspressoGameSessionRegistry());
  private final SessionId sessionId = new SessionId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void attemptMove_appliesLegalMoveAndReturnsResultingFen() {
    MoveExecutionResult result = attempt("e2", "e4");

    assertTrue(result.accepted());
    assertEquals(32, result.newSnapshot().pieces().size());
    assertEquals("e4", result.move().orElseThrow().to().toAlgebraic());
    assertEquals("e4", result.move().orElseThrow().san().getValue());
  }

  @Test
  void attemptMove_rejectsIllegalMoveAndLeavesPositionUnchanged() {
    MoveExecutionResult rejected = attempt("e2", "e5");

    assertFalse(rejected.accepted());
    assertEquals(
        32, rejected.newSnapshot().pieces().size());
    assertTrue(rejected.rejectionReason().orElseThrow().contains("Illegal move"));
  }

  private MoveExecutionResult attempt(String from, String to) {
    return service.attemptMove(
        new MoveRequest(sessionId, Square.of(from), Square.of(to), Optional.empty()));
  }
}
