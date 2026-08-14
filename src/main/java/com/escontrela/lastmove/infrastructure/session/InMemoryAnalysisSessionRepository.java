package com.escontrela.lastmove.infrastructure.session;

import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Process-local implementation of {@link AnalysisSessionRepository}.
 *
 * <p>It retains analysis aggregates for the current process and preserves insertion order. It
 * never stores a globally active session because each UI workflow owns its own selection.
 */
@Repository
public final class InMemoryAnalysisSessionRepository implements AnalysisSessionRepository {

  private final Map<AnalysisSessionId, AnalysisSession> sessions = new LinkedHashMap<>();

  @Override
  public synchronized void save(AnalysisSession session) {
    AnalysisSession required = Objects.requireNonNull(session, "session must not be null");
    sessions.put(required.id(), required);
  }

  @Override
  public synchronized Optional<AnalysisSession> findById(AnalysisSessionId sessionId) {
    return Optional.ofNullable(
        sessions.get(Objects.requireNonNull(sessionId, "sessionId must not be null")));
  }

  @Override
  public synchronized boolean deleteById(AnalysisSessionId sessionId) {
    return sessions.remove(
            Objects.requireNonNull(sessionId, "sessionId must not be null"))
        != null;
  }

  @Override
  public synchronized List<AnalysisSession> findAllByMostRecent() {
    List<AnalysisSession> newestFirst = new ArrayList<>(sessions.values());
    Collections.reverse(newestFirst);
    return List.copyOf(newestFirst);
  }
}
