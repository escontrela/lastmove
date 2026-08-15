package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ComputerEngineSettingsServiceTest {

  @Test
  void returnsTheConfiguredDefaultUntilTheUserStoresAnOverride() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    ComputerEngineSettingsService service =
        new ComputerEngineSettingsService(repository, "/default/sunfish-uci");

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
    ComputerEngineSettingsService service =
        new ComputerEngineSettingsService(repository, "/default/sunfish-uci");

    assertThrows(IllegalArgumentException.class, () -> service.updateSunfishExecutable("  "));

    assertTrue(repository.settings.isEmpty());
  }

  private static final class InMemorySettingsRepository
      implements ComputerEngineSettingsRepository {

    private Optional<ComputerEngineSettings> settings = Optional.empty();

    @Override
    public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
      return settings.filter(value -> value.engineId().equals(engineId));
    }

    @Override
    public void save(ComputerEngineSettings settings) {
      this.settings = Optional.of(settings);
    }
  }
}
