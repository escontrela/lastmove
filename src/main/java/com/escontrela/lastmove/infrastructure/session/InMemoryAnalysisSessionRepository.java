package com.escontrela.lastmove.infrastructure.session;

import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.ArrayList;
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
  private final List<AnalysisSessionId> displayOrder = new ArrayList<>();

  @Override
  public synchronized void save(AnalysisSession session) {
    AnalysisSession required = Objects.requireNonNull(session, "session must not be null");
    if (!sessions.containsKey(required.id())) {
      displayOrder.addFirst(required.id());
    }
    sessions.put(required.id(), required);
  }

  @Override
  public synchronized Optional<AnalysisSession> findById(AnalysisSessionId sessionId) {
    return Optional.ofNullable(
        sessions.get(Objects.requireNonNull(sessionId, "sessionId must not be null")));
  }

  @Override
  public synchronized boolean deleteById(AnalysisSessionId sessionId) {
    AnalysisSessionId required =
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    displayOrder.remove(required);
    return sessions.remove(required) != null;
  }

  @Override
  public synchronized List<AnalysisSession> findAllInDisplayOrder() {
    return displayOrder.stream().map(sessions::get).toList();
  }

  @Override
  public synchronized boolean moveToIndex(AnalysisSessionId sessionId, int targetIndex) {
    AnalysisSessionId required =
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    int currentIndex = displayOrder.indexOf(required);
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= displayOrder.size()) {
      return false;
    }
    if (currentIndex == targetIndex) {
      return true;
    }
    displayOrder.remove(currentIndex);
    displayOrder.add(targetIndex, required);
    return true;
  }
}
