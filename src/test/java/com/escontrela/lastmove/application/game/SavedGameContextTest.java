package com.escontrela.lastmove.application.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SavedGameContextTest {
  @Test void acceptsTournamentGamesWithoutComputerConfigurationAndKeepsBothParticipants() {
    var white = com.escontrela.lastmove.domain.player.PlayerId.of(11L);
    var black = com.escontrela.lastmove.domain.player.PlayerId.of(12L);

    var context = new SavedGameContext(GameType.LICHESS_BOT_TOURNAMENT, Optional.of(white), Optional.empty(), List.of(white, black));

    assertEquals(GameType.LICHESS_BOT_TOURNAMENT, context.gameType());
    assertEquals(List.of(white, black), context.participantPlayerIds());
    assertEquals(Optional.empty(), context.computerConfiguration());
  }
}
