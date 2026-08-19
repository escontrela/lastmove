package com.escontrela.lastmove.infrastructure.engine.sunfish;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in smoke test against the locally installed Sunfish wrapper and its real Python runtime. */
@EnabledIfSystemProperty(named = "lastmove.sunfish.integration", matches = "true")
class SunfishRealEngineIntegrationTest {

  private static final String DEFAULT_SUNFISH_EXECUTABLE =
      "/Users/davidpe/Library/Python/3.9/bin/sunfish-uci";

  @Test
  void answersWithALegalMoveFromTheKnownInitialFen() throws Exception {
    Path executable =
        Path.of(
            System.getProperty("lastmove.sunfish.executable", DEFAULT_SUNFISH_EXECUTABLE));
    ComputerEngineSettingsRepository repository = repository(executable);
    var settingsService =
        new ComputerEngineSettingsService(repository, executable.toString(), "/default/maia");
    var rulesEngine = new ChesspressoRulesEngine();
    var provider =
        new SunfishComputerMoveEngineProvider(
            settingsService, new FenService(), new SunfishExecutableResolver());
    var healthCheck = new SunfishComputerEngineHealthCheck(provider, rulesEngine);

    var health = healthCheck.check().toCompletableFuture().get(10, TimeUnit.SECONDS);

    assertTrue(health.available(), health.message());
    assertTrue(health.probeMove().isPresent());
    assertTrue(
        rulesEngine.execute(rulesEngine.startingPosition(), health.probeMove().orElseThrow())
            .accepted());
  }

  private static ComputerEngineSettingsRepository repository(Path executable) {
    return new ComputerEngineSettingsRepository() {
      @Override
      public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
        return Optional.of(new ComputerEngineSettings(ComputerEngineIds.SUNFISH, executable));
      }

      @Override
      public void save(ComputerEngineSettings settings) {}

      @Override
      public void deleteByEngineId(String engineId) {}
    };
  }
}
