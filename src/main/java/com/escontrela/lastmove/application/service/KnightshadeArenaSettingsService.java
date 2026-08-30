package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.application.arena.KnightshadeArenaSettingsRepository;
import com.escontrela.lastmove.application.arena.LichessBotAccount;
import com.escontrela.lastmove.application.arena.LichessBotAccountValidationException;
import com.escontrela.lastmove.application.arena.LichessBotAccountVerifier;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Coordinates user-local Arena preferences and safe validation of the configured bot account. */
@Service
public final class KnightshadeArenaSettingsService {
  private final KnightshadeArenaSettingsRepository repository;
  private final LichessBotAccountVerifier accountVerifier;

  public KnightshadeArenaSettingsService(
      KnightshadeArenaSettingsRepository repository, LichessBotAccountVerifier accountVerifier) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.accountVerifier = Objects.requireNonNull(accountVerifier, "accountVerifier must not be null");
  }

  public KnightshadeArenaSettings settings() {
    return repository.loadSettings();
  }

  public void updateSettings(KnightshadeArenaSettings settings) {
    repository.saveSettings(Objects.requireNonNull(settings, "settings must not be null"));
  }

  public boolean hasBotToken() {
    return repository.findBotToken().isPresent();
  }

  /** Stores the supplied secret only in the user-local repository. */
  public void updateBotToken(String token) {
    String required = Objects.requireNonNull(token, "token must not be null").trim();
    if (required.isEmpty()) throw new IllegalArgumentException("Lichess bot token must not be blank");
    repository.saveBotToken(required);
  }

  public void clearBotToken() {
    repository.deleteBotToken();
  }

  /** Returns only the non-secret identity captured by the last successful validation. */
  public java.util.Optional<LichessBotAccount> configuredBotAccount() {
    return repository.findValidatedBotAccount();
  }

  /** Validates the saved token and confirms that it belongs to a Lichess bot account. */
  public LichessBotAccount validateConfiguredBotAccount() {
    String token = repository.findBotToken().orElseThrow(
        () -> new LichessBotAccountValidationException("Save a Lichess bot token before validating it."));
    LichessBotAccount verified = accountVerifier.verifyBotToken(token);
    Optional<Integer> previous = repository.findValidatedBotAccount()
        .filter(old -> old.id().equalsIgnoreCase(verified.id()))
        .flatMap(LichessBotAccount::standardRating);
    LichessBotAccount account = new LichessBotAccount(verified.id(), verified.username(),
        verified.blitzRating(), verified.rapidRating(), verified.standardRating(), previous);
    repository.saveValidatedBotAccount(account);
    return account;
  }
}
