package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComputerEngineSettingsServiceTest {

  private static final String DEFAULT_MAIA_WEIGHTS = "/default/maia";

  @Test
  void returnsTheConfiguredDefaultUntilTheUserStoresAnOverride() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertEquals(
        Path.of("/default/sunfish-uci"), service.sunfishSettings().executablePath());

    ComputerEngineSettings updated =
        service.updateSunfishExecutable(" /custom/sunfish-uci ");

    assertEquals(Path.of("/custom/sunfish-uci"), updated.executablePath());
    assertEquals(updated, service.sunfishSettings());
  }

  @Test
  void rejectsABlankExecutableWithoutChangingStoredSettings() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertThrows(IllegalArgumentException.class, () -> service.updateSunfishExecutable("  "));

    assertTrue(repository.settings.isEmpty());
  }

  @Test
  void returnsNoMaiaExecutableUntilAnOverrideIsStored() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertTrue(service.maiaExecutable().isEmpty());
  }

  @Test
  void storesAndReturnsTheMaiaExecutableOverride() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    ComputerEngineSettings updated = service.updateMaiaExecutable(" /opt/homebrew/bin/lc0 ");

    assertEquals(Path.of("/opt/homebrew/bin/lc0"), updated.executablePath());
    assertEquals(
        Optional.of(Path.of("/opt/homebrew/bin/lc0")), service.maiaExecutable());
  }

  @Test
  void returnsTheConfiguredDefaultMaiaWeightsLocation() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertEquals(Path.of(DEFAULT_MAIA_WEIGHTS), service.maiaWeightsLocation());
  }

  @Test
  void storesAndReturnsTheMaiaWeightsLocationOverride() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    ComputerEngineSettings updated =
        service.updateMaiaWeightsLocation(" /opt/homebrew/Cellar/lc0/0.32.1/libexec ");

    assertEquals(Path.of("/opt/homebrew/Cellar/lc0/0.32.1/libexec"), updated.executablePath());
    assertEquals(
        Path.of("/opt/homebrew/Cellar/lc0/0.32.1/libexec"), service.maiaWeightsLocation());
  }

  @Test
  void clearsTheMaiaExecutableOverride() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);
    service.updateMaiaExecutable(" /opt/homebrew/bin/lc0 ");
    assertTrue(service.maiaExecutable().isPresent());

    service.clearMaiaExecutable();

    assertTrue(service.maiaExecutable().isEmpty());
  }

  @Test
  void returnsTheDefaultThinkingTimeUntilAnOverrideIsStored() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertEquals(Duration.ofMillis(500), service.thinkingTime("knightshade"));

    service.updateThinkingTime("knightshade", Duration.ofSeconds(5));

    assertEquals(Duration.ofSeconds(5), service.thinkingTime("knightshade"));
  }

  @Test
  void rejectsANonPositiveThinkingTimeWithoutStoring() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateThinkingTime("knightshade", Duration.ZERO));

    assertEquals(Duration.ofMillis(500), service.thinkingTime("knightshade"));
  }

  @Test
  void storesClearsAndReturnsTheDefaultAnalysisEngine() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service = service(repository);

    assertTrue(service.defaultAnalysisEngineId().isEmpty());

    service.updateDefaultAnalysisEngineId(Optional.of(" sunfish "));
    assertEquals(Optional.of("sunfish"), service.defaultAnalysisEngineId());

    service.updateDefaultAnalysisEngineId(Optional.empty());
    assertTrue(service.defaultAnalysisEngineId().isEmpty());
  }

  private static ComputerEngineSettingsService service(InMemorySettingsRepository repository) {
    return new ComputerEngineSettingsService(
        repository, "/default/sunfish-uci", DEFAULT_MAIA_WEIGHTS);
  }

  private static final class InMemorySettingsRepository
      implements ComputerEngineSettingsRepository {

    private final Map<String, ComputerEngineSettings> settings = new HashMap<>();
    private final Map<String, Long> thinkingTimes = new HashMap<>();
    private String defaultAnalysisEngineId;

    @Override
    public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
      return Optional.ofNullable(settings.get(engineId));
    }

    @Override
    public void save(ComputerEngineSettings settings) {
      this.settings.put(settings.engineId(), settings);
    }

    @Override
    public void deleteByEngineId(String engineId) {
      settings.remove(engineId);
    }

    @Override
    public Optional<Long> findThinkingTimeMillis(String engineId) {
      return Optional.ofNullable(thinkingTimes.get(engineId));
    }

    @Override
    public void saveThinkingTimeMillis(String engineId, long thinkingTimeMillis) {
      thinkingTimes.put(engineId, thinkingTimeMillis);
    }

    @Override
    public Optional<String> findDefaultAnalysisEngineId() {
      return Optional.ofNullable(defaultAnalysisEngineId);
    }

    @Override
    public void saveDefaultAnalysisEngineId(String engineId) {
      this.defaultAnalysisEngineId = engineId;
    }

    @Override
    public void deleteDefaultAnalysisEngineId() {
      this.defaultAnalysisEngineId = null;
    }
  }
}
