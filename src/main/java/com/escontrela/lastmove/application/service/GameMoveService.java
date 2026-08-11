package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.MoveRequest;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoGameSessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class GameMoveService {

  private final ChesspressoGameSessionRegistry sessionRegistry;

  public GameMoveService(ChesspressoGameSessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  public MoveExecutionResult attemptMove(MoveRequest request) {
    return sessionRegistry.sessionFor(request.sessionId()).tryMove(request.toMoveCommand());
  }

  public PositionSnapshot currentSnapshot(SessionId sessionId) {
    return sessionRegistry.sessionFor(sessionId).currentSnapshot();
  }
}
