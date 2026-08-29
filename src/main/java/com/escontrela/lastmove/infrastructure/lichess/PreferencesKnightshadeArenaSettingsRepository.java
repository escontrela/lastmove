package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.application.arena.KnightshadeArenaSettingsRepository;
import java.util.Optional;
import java.util.prefs.Preferences;
import org.springframework.stereotype.Repository;

/** Java Preferences store for local Arena settings; this data is deliberately outside SQLite. */
@Repository
public class PreferencesKnightshadeArenaSettingsRepository
    implements KnightshadeArenaSettingsRepository {
  private static final String TOKEN_KEY = "lichess.bot-token";
  private static final String MAXIMUM_GAMES_KEY = "arena.maximum-concurrent-games";
  private static final String AUTO_ACCEPT_KEY = "arena.automatic-challenge-acceptance";

  private final Preferences preferences;

  public PreferencesKnightshadeArenaSettingsRepository() {
    this(Preferences.userNodeForPackage(PreferencesKnightshadeArenaSettingsRepository.class).node("knightshade-arena"));
  }

  PreferencesKnightshadeArenaSettingsRepository(Preferences preferences) {
    this.preferences = preferences;
  }

  @Override
  public KnightshadeArenaSettings loadSettings() {
    int maximum = preferences.getInt(
        MAXIMUM_GAMES_KEY, KnightshadeArenaSettings.DEFAULT_MAXIMUM_CONCURRENT_GAMES);
    boolean automatic = preferences.getBoolean(AUTO_ACCEPT_KEY, false);
    try {
      return new KnightshadeArenaSettings(maximum, automatic);
    } catch (IllegalArgumentException ignored) {
      return KnightshadeArenaSettings.defaults();
    }
  }

  @Override
  public void saveSettings(KnightshadeArenaSettings settings) {
    preferences.putInt(MAXIMUM_GAMES_KEY, settings.maximumConcurrentGames());
    preferences.putBoolean(AUTO_ACCEPT_KEY, settings.automaticChallengeAcceptance());
  }

  @Override
  public Optional<String> findBotToken() {
    return Optional.ofNullable(preferences.get(TOKEN_KEY, null)).map(String::trim).filter(token -> !token.isEmpty());
  }

  @Override
  public void saveBotToken(String token) {
    preferences.put(TOKEN_KEY, token);
  }

  @Override
  public void deleteBotToken() {
    preferences.remove(TOKEN_KEY);
  }
}
