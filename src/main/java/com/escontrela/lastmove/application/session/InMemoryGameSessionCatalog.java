package com.escontrela.lastmove.application.session;

import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.GameSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Application-owned, process-local cache of the analysis sessions currently open in LastMove.
 *
 * <p>It deliberately has no persistence concern: closing the process discards its contents. The
 * application service is the only client that should mutate sessions retrieved from this catalog.
 */
@Component
public final class InMemoryGameSessionCatalog {

  private final Map<SessionId, GameSession> sessions = new LinkedHashMap<>();
  private final Map<SessionId, String> titles = new LinkedHashMap<>();
  private SessionId activeSessionId;

  /** Adds a new session and makes it the active one. */
  public synchronized void addAndActivate(GameSession session, String title) {
    Objects.requireNonNull(session, "session must not be null");
    Objects.requireNonNull(title, "title must not be null");
    if (sessions.putIfAbsent(session.id(), session) != null) {
      throw new IllegalArgumentException("A session with the same id is already open");
    }
    titles.put(session.id(), title);
    activeSessionId = session.id();
  }

  /** Returns a session by identity without changing which session is active. */
  public synchronized Optional<GameSession> find(SessionId sessionId) {
    return Optional.ofNullable(sessions.get(Objects.requireNonNull(sessionId, "sessionId must not be null")));
  }

  /** Makes an already-open session active. */
  public synchronized boolean activate(SessionId sessionId) {
    if (!sessions.containsKey(Objects.requireNonNull(sessionId, "sessionId must not be null"))) {
      return false;
    }
    activeSessionId = sessionId;
    return true;
  }

  /** Returns the active session, if one has been created. */
  public synchronized Optional<GameSession> active() {
    return Optional.ofNullable(activeSessionId).map(sessions::get);
  }

  /** Returns sessions in creation order, suitable for a session-selection dialog. */
  public synchronized List<GameSession> all() {
    List<GameSession> newestFirst = new java.util.ArrayList<>(sessions.values());
    java.util.Collections.reverse(newestFirst);
    return List.copyOf(newestFirst);
  }

  /** Returns the title assigned when the session was created. */
  public synchronized Optional<String> titleOf(SessionId sessionId) {
    return Optional.ofNullable(titles.get(Objects.requireNonNull(sessionId, "sessionId must not be null")));
  }
}
