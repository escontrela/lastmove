package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.application.arena.KnightshadeArenaSettingsRepository;
import com.escontrela.lastmove.application.arena.LichessBotAccount;
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
  private static final String AUTO_RECONNECT_KEY = "arena.auto-reconnect";
  private static final String VALIDATED_ACCOUNT_ID_KEY = "lichess.validated-account.id";
  private static final String VALIDATED_ACCOUNT_USERNAME_KEY = "lichess.validated-account.username";
  private static final String VALIDATED_ACCOUNT_RATING_KEY = "lichess.validated-account.standard-rating";
  private static final String VALIDATED_ACCOUNT_PREVIOUS_RATING_KEY = "lichess.validated-account.previous-standard-rating";

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
    boolean automatic = preferences.getBoolean(AUTO_ACCEPT_KEY, false), reconnect = preferences.getBoolean(AUTO_RECONNECT_KEY, false);
    try {
      return new KnightshadeArenaSettings(maximum, automatic, reconnect);
    } catch (IllegalArgumentException ignored) {
      return KnightshadeArenaSettings.defaults();
    }
  }

  @Override
  public void saveSettings(KnightshadeArenaSettings settings) {
    preferences.putInt(MAXIMUM_GAMES_KEY, settings.maximumConcurrentGames());
    preferences.putBoolean(AUTO_ACCEPT_KEY, settings.automaticChallengeAcceptance());
    preferences.putBoolean(AUTO_RECONNECT_KEY, settings.autoReconnect());
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

  @Override
  public Optional<LichessBotAccount> findValidatedBotAccount() {
    String id = preferences.get(VALIDATED_ACCOUNT_ID_KEY, "").trim();
    String username = preferences.get(VALIDATED_ACCOUNT_USERNAME_KEY, "").trim();
    if (id.isEmpty() || username.isEmpty()) return Optional.empty();
    try {
      String rating = preferences.get(VALIDATED_ACCOUNT_RATING_KEY, "").trim();
      Optional<Integer> current = rating.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(rating));
      String previousRating = preferences.get(VALIDATED_ACCOUNT_PREVIOUS_RATING_KEY, "").trim();
      Optional<Integer> previous = previousRating.isEmpty() ? Optional.empty() : Optional.of(Integer.parseInt(previousRating));
      return Optional.of(new LichessBotAccount(id, username, current, previous));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  @Override
  public void saveValidatedBotAccount(LichessBotAccount account) {
    preferences.put(VALIDATED_ACCOUNT_ID_KEY, account.id());
    preferences.put(VALIDATED_ACCOUNT_USERNAME_KEY, account.username());
    account.standardRating().ifPresentOrElse(rating -> preferences.put(VALIDATED_ACCOUNT_RATING_KEY, rating.toString()),
        () -> preferences.remove(VALIDATED_ACCOUNT_RATING_KEY));
    account.previousStandardRating().ifPresentOrElse(rating -> preferences.put(VALIDATED_ACCOUNT_PREVIOUS_RATING_KEY, rating.toString()),
        () -> preferences.remove(VALIDATED_ACCOUNT_PREVIOUS_RATING_KEY));
  }
}
