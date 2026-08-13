package com.escontrela.lastmove.infrastructure.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.SessionId;
import com.escontrela.lastmove.domain.game.CastlingRights;
import com.escontrela.lastmove.domain.game.GameSession;
import com.escontrela.lastmove.domain.game.GameSessionOrigin;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryGameSessionRepositoryTest {

  @Test
  void storesByIdentityAndListsNewestSessionsFirstWithoutGlobalSelection() {
    InMemoryGameSessionRepository repository = new InMemoryGameSessionRepository();
    GameSession first = session("First");
    GameSession second = session("Second");

    repository.save(first);
    repository.save(second);

    assertSame(first, repository.findById(first.id()).orElseThrow());
    assertEquals(List.of(second, first), repository.findAllByMostRecent());
  }

  private static GameSession session(String title) {
    return new GameSession(
        SessionId.random(),
        title,
        GameSessionOrigin.INITIAL_POSITION,
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
