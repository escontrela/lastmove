package com.escontrela.lastmove.infrastructure.lichess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.application.arena.LichessBotAccount;
import java.util.UUID;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class PreferencesKnightshadeArenaSettingsRepositoryTest {

  @Test
  void storesArenaPreferencesAndCanRemoveTheToken() throws BackingStoreException {
    Preferences node = Preferences.userRoot().node("lastmove-test/" + UUID.randomUUID());
    try {
      PreferencesKnightshadeArenaSettingsRepository repository =
          new PreferencesKnightshadeArenaSettingsRepository(node);

      repository.saveSettings(new KnightshadeArenaSettings(4, true));
      repository.saveBotToken("bot-secret");
      repository.saveValidatedBotAccount(new LichessBotAccount("knightshade", "Knightshade Arena"));

      assertEquals(new KnightshadeArenaSettings(4, true), repository.loadSettings());
      assertEquals("bot-secret", repository.findBotToken().orElseThrow());
      assertEquals(new LichessBotAccount("knightshade", "Knightshade Arena"), repository.findValidatedBotAccount().orElseThrow());

      repository.deleteBotToken();

      assertTrue(repository.findBotToken().isEmpty());
    } finally {
      node.removeNode();
    }
  }

  @Test
  void preservesEachDisplayedLichessRating() throws BackingStoreException {
    Preferences node = Preferences.userRoot().node("lastmove-test/" + UUID.randomUUID());
    try {
      PreferencesKnightshadeArenaSettingsRepository repository = new PreferencesKnightshadeArenaSettingsRepository(node);
      LichessBotAccount account = new LichessBotAccount("knightshade", "Knightshade Arena",
          Optional.of(1840), Optional.of(1760), Optional.of(1690), Optional.empty());

      repository.saveValidatedBotAccount(account);

      assertEquals(account, repository.findValidatedBotAccount().orElseThrow());
    } finally {
      node.removeNode();
    }
  }
}
