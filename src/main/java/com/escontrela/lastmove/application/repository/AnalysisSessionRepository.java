package com.escontrela.lastmove.application.repository;

import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for chess-analysis sessions.
 *
 * <p>The application owns this contract and knows nothing about whether sessions are retained in
 * process memory or stored in a database. The repository deliberately has no active-session state.
 */
public interface AnalysisSessionRepository {

  /** Stores a new session or the latest state of an existing one. */
  void save(AnalysisSession session);

  /** Finds one analysis session by its stable identity. */
  Optional<AnalysisSession> findById(AnalysisSessionId sessionId);

  /** Deletes one retained session, returning whether it existed. */
  boolean deleteById(AnalysisSessionId sessionId);

  /** Lists retained sessions in their user-controlled display order. */
  List<AnalysisSession> findAllInDisplayOrder();

  /** Moves one retained session to a zero-based position in the display order. */
  boolean moveToIndex(AnalysisSessionId sessionId, int targetIndex);
}
