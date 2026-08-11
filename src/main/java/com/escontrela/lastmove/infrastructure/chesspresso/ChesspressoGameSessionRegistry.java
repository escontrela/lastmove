package com.escontrela.lastmove.infrastructure.chesspresso;

import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.ChessGameSession;
import com.escontrela.lastmove.domain.notation.Fen;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Creates and owns the Chesspresso session associated with each application session id. */
@Component
public class ChesspressoGameSessionRegistry {

  private final Map<SessionId, ChessGameSession> sessions = new ConcurrentHashMap<>();

  public ChessGameSession sessionFor(SessionId sessionId) {
    return sessions.computeIfAbsent(
        sessionId, ignored -> new ChesspressoGameSession(Fen.startingPosition()));
  }
}
