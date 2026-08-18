package com.escontrela.lastmove.infrastructure.engine.maia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in smoke test against a locally installed lc0 and the Maia 1100 weights file. */
@EnabledIfSystemProperty(named = "lastmove.maia.integration", matches = "true")
class MaiaRealEngineIntegrationTest {

  private static final String DEFAULT_LC0 = "/opt/homebrew/bin/lc0";
  private static final String DEFAULT_MODELS =
      System.getProperty("user.home") + "/.local/share/maia";

  @Test
  void answersWithALegalMoveFromTheKnownInitialFen() throws Exception {
    Path executable =
        Path.of(System.getProperty("lastmove.maia.executable", DEFAULT_LC0));
    Path models = Path.of(System.getProperty("lastmove.maia.models", DEFAULT_MODELS));
    var rulesEngine = new ChesspressoRulesEngine();
    var settingsService = settingsService(executable, models);
    var provider =
        new MaiaComputerMoveEngineProvider(
            MaiaEngineProfile.MAIA_1100,
            settingsService,
            new FenService(),
            new MaiaExecutableResolver(),
            4);
    var healthCheck = new MaiaComputerEngineHealthCheck(provider, rulesEngine);

    var health = healthCheck.check().toCompletableFuture().get(120, TimeUnit.SECONDS);

    assertTrue(health.available(), health.message());
    assertTrue(health.probeMove().isPresent());
    assertTrue(
        rulesEngine.execute(rulesEngine.startingPosition(), health.probeMove().orElseThrow())
            .accepted());
  }

  private static ComputerEngineSettingsService settingsService(Path executable, Path models) {
    ComputerEngineSettingsRepository repository =
        new ComputerEngineSettingsRepository() {
          @Override
          public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
            return Optional.of(new ComputerEngineSettings(ComputerEngineIds.MAIA, executable));
          }

          @Override
          public void save(ComputerEngineSettings settings) {}

          @Override
          public void deleteByEngineId(String engineId) {}
        };
    return new ComputerEngineSettingsService(repository, executable.toString(), models.toString());
  }
}
