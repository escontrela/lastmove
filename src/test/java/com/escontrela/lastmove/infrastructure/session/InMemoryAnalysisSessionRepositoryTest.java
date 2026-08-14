package com.escontrela.lastmove.infrastructure.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryAnalysisSessionRepositoryTest {

  @Test
  void storesByIdentityAndListsNewestSessionsFirstWithoutGlobalSelection() {
    InMemoryAnalysisSessionRepository repository = new InMemoryAnalysisSessionRepository();
    AnalysisSession first = session("First");
    AnalysisSession second = session("Second");

    repository.save(first);
    repository.save(second);

    assertSame(first, repository.findById(first.id()).orElseThrow());
    assertEquals(List.of(second, first), repository.findAllInDisplayOrder());
  }

  @Test
  void deletesOnlyTheRequestedSession() {
    InMemoryAnalysisSessionRepository repository = new InMemoryAnalysisSessionRepository();
    AnalysisSession retained = session("Retained");
    AnalysisSession deleted = session("Deleted");
    repository.save(retained);
    repository.save(deleted);

    assertTrue(repository.deleteById(deleted.id()));
    assertFalse(repository.deleteById(deleted.id()));
    assertEquals(List.of(retained), repository.findAllInDisplayOrder());
  }

  @Test
  void movesSessionsWithinTheirDisplayOrder() {
    InMemoryAnalysisSessionRepository repository = new InMemoryAnalysisSessionRepository();
    AnalysisSession first = session("First");
    AnalysisSession second = session("Second");
    AnalysisSession third = session("Third");
    repository.save(first);
    repository.save(second);
    repository.save(third);

    assertTrue(repository.moveToIndex(first.id(), 0));
    assertEquals(List.of(first, third, second), repository.findAllInDisplayOrder());
    assertFalse(repository.moveToIndex(second.id(), 3));
  }

  private static AnalysisSession session(String title) {
    return new AnalysisSession(
        AnalysisSessionId.random(),
        title,
        AnalysisOrigin.INITIAL_POSITION,
        new PositionSnapshot(
            List.of(),
            PieceColor.WHITE,
            CastlingRights.initial(),
            Optional.empty(),
            0,
            1,
            Optional.empty(),
            false,
            false,
            false));
  }
}
