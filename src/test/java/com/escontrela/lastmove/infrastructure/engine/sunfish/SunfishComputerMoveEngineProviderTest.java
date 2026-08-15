package com.escontrela.lastmove.infrastructure.engine.sunfish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SunfishComputerMoveEngineProviderTest {

  @Test
  void createsAnIndependentEngineFromTheConfiguredExecutable() {
    Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
    var provider = provider(javaExecutable);

    try (var first = provider.create(); var second = provider.create()) {
      assertEquals("sunfish", first.descriptor().id());
      assertEquals("Sunfish", first.descriptor().displayName());
      org.junit.jupiter.api.Assertions.assertNotSame(first, second);
    }
  }

  @Test
  void rejectsAMissingExecutableBeforeStartingAProcess() {
    var provider = provider(Path.of("/missing/lastmove/sunfish-uci"));

    assertThrows(ComputerEngineException.class, provider::create);
  }

  private static SunfishComputerMoveEngineProvider provider(Path executable) {
    ComputerEngineSettingsRepository repository =
        new ComputerEngineSettingsRepository() {
          @Override
          public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
            return Optional.of(new ComputerEngineSettings(engineId, executable));
          }

          @Override
          public void save(ComputerEngineSettings settings) {}
        };
    return new SunfishComputerMoveEngineProvider(
        new ComputerEngineSettingsService(repository, executable.toString()), new FenService());
  }
}
