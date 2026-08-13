package com.escontrela.lastmove.infrastructure.session;

import com.escontrela.lastmove.application.session.GameSessionRepository;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.GameSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Process-local implementation of {@link GameSessionRepository}.
 *
 * <p>It retains aggregate instances only for the current application process. It deliberately
 * does not own a notion of an active session: selection belongs to each UI workflow.
 */
@Repository
public final class InMemoryGameSessionRepository implements GameSessionRepository {

  private final Map<SessionId, GameSession> sessions = new LinkedHashMap<>();

  @Override
  public synchronized void save(GameSession session) {
    Objects.requireNonNull(session, "session must not be null");
    sessions.put(session.id(), session);
  }

  @Override
  public synchronized Optional<GameSession> findById(SessionId sessionId) {
    return Optional.ofNullable(sessions.get(Objects.requireNonNull(sessionId, "sessionId must not be null")));
  }

  @Override
  public synchronized List<GameSession> findAllByMostRecent() {
    List<GameSession> newestFirst = new ArrayList<>(sessions.values());
    Collections.reverse(newestFirst);
    return List.copyOf(newestFirst);
  }
}
