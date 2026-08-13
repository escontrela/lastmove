package com.escontrela.lastmove.application.session;

import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.GameSession;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for chess-analysis sessions.
 *
 * <p>The application depends on this contract rather than on a storage mechanism. Implementations
 * may keep sessions in memory today and persist them in a database later.
 */
public interface GameSessionRepository {

  /** Stores a new session or persists the current state of an existing one. */
  void save(GameSession session);

  /** Returns one stored session by its stable identity. */
  Optional<GameSession> findById(SessionId sessionId);

  /** Returns stored sessions from most recently created to least recently created. */
  List<GameSession> findAllByMostRecent();
}
