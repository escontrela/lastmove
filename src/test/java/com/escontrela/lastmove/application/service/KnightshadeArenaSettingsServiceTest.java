package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.application.arena.KnightshadeArenaSettingsRepository;
import com.escontrela.lastmove.application.arena.LichessBotAccount;
import com.escontrela.lastmove.application.arena.LichessBotAccountValidationException;
import com.escontrela.lastmove.application.arena.LichessBotAccountVerifier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnightshadeArenaSettingsServiceTest {

  @Test
  void storesNonSecretPreferencesAndKeepsTheTokenOutOfTheirRepresentation() {
    InMemoryRepository repository = new InMemoryRepository();
    KnightshadeArenaSettingsService service = service(repository, token -> new LichessBotAccount("bot-id", "Knightshade"));

    service.updateSettings(new KnightshadeArenaSettings(3, true));
    service.updateBotToken(" secret-token ");

    assertEquals(new KnightshadeArenaSettings(3, true), service.settings());
    assertTrue(service.hasBotToken());
    assertFalse(service.settings().toString().contains("secret-token"));
    assertFalse(service.toString().contains("secret-token"));
  }

  @Test
  void validatesOnlyTheSavedToken() {
    InMemoryRepository repository = new InMemoryRepository();
    String[] verifiedToken = new String[1];
    KnightshadeArenaSettingsService service = service(repository, token -> {
      verifiedToken[0] = token;
      return new LichessBotAccount("bot-id", "Knightshade");
    });
    service.updateBotToken("token-value");

    assertEquals(new LichessBotAccount("bot-id", "Knightshade"), service.validateConfiguredBotAccount());
    assertEquals("token-value", verifiedToken[0]);
  }

  @Test
  void rejectsBlankTokensAndDoesNotStoreThem() {
    InMemoryRepository repository = new InMemoryRepository();
    KnightshadeArenaSettingsService service = service(repository, token -> new LichessBotAccount("bot-id", "Knightshade"));

    assertThrows(IllegalArgumentException.class, () -> service.updateBotToken(" "));
    assertFalse(service.hasBotToken());
  }

  @Test
  void explainsThatAStoredTokenIsRequiredForValidation() {
    KnightshadeArenaSettingsService service = service(new InMemoryRepository(),
        token -> new LichessBotAccount("bot-id", "Knightshade"));

    LichessBotAccountValidationException exception = assertThrows(
        LichessBotAccountValidationException.class, service::validateConfiguredBotAccount);

    assertEquals("Save a Lichess bot token before validating it.", exception.getMessage());
  }

  @Test
  void clearsTheStoredToken() {
    InMemoryRepository repository = new InMemoryRepository();
    KnightshadeArenaSettingsService service = service(repository, token -> new LichessBotAccount("bot-id", "Knightshade"));
    service.updateBotToken("token-value");

    service.clearBotToken();

    assertFalse(service.hasBotToken());
  }

  private static KnightshadeArenaSettingsService service(
      InMemoryRepository repository, LichessBotAccountVerifier verifier) {
    return new KnightshadeArenaSettingsService(repository, verifier);
  }

  private static final class InMemoryRepository implements KnightshadeArenaSettingsRepository {
    private KnightshadeArenaSettings settings = KnightshadeArenaSettings.defaults();
    private String token;

    @Override public KnightshadeArenaSettings loadSettings() { return settings; }
    @Override public void saveSettings(KnightshadeArenaSettings settings) { this.settings = settings; }
    @Override public Optional<String> findBotToken() { return Optional.ofNullable(token); }
    @Override public void saveBotToken(String token) { this.token = token; }
    @Override public void deleteBotToken() { token = null; }
  }
}
