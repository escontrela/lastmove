package com.escontrela.lastmove.infrastructure.engine.sunfish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.domain.service.FenService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SunfishComputerMoveEngineProviderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void createsAnIndependentEngineFromTheConfiguredExecutable() throws IOException {
    var provider = provider(wrapper("sunfish-uci"));

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

          @Override
          public void deleteByEngineId(String engineId) {}
        };
    return new SunfishComputerMoveEngineProvider(
        new ComputerEngineSettingsService(repository, executable.toString(), "/default/maia"),
        new FenService(),
        new SunfishExecutableResolver());
  }

  private Path wrapper(String name) throws IOException {
    Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
    Path wrapper = temporaryDirectory.resolve(name);
    Files.writeString(wrapper, "#!" + javaExecutable + System.lineSeparator());
    wrapper.toFile().setExecutable(true);
    return wrapper;
  }
}
