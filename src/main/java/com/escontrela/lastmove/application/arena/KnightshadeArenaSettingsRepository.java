package com.escontrela.lastmove.application.arena;

import java.util.Optional;

/** User-local storage boundary for Arena preferences and its separate, secret bot token. */
public interface KnightshadeArenaSettingsRepository {
  KnightshadeArenaSettings loadSettings();

  void saveSettings(KnightshadeArenaSettings settings);

  Optional<String> findBotToken();

  void saveBotToken(String token);

  void deleteBotToken();

  Optional<LichessBotAccount> findValidatedBotAccount();

  void saveValidatedBotAccount(LichessBotAccount account);
}
