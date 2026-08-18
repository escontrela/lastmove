package com.escontrela.lastmove.infrastructure.engine.maia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
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

class MaiaComputerMoveEngineProviderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void createsIndependentEnginesFromTheConfiguredExecutable() throws IOException {
    Path lc0 = executable(temporaryDirectory.resolve("lc0"));
    Files.writeString(temporaryDirectory.resolve("maia-1100.pb.gz"), "weights");
    var provider = provider(MaiaEngineProfile.MAIA_1100, lc0);

    try (var first = provider.create(); var second = provider.create()) {
      assertEquals("maia-1100", first.descriptor().id());
      assertEquals("Maia", first.descriptor().displayName());
      assertEquals("1100", first.descriptor().version());
      assertNotSame(first, second);
    }
  }

  @Test
  void rejectsMissingWeightsBeforeStartingAProcess() throws IOException {
    Path lc0 = executable(temporaryDirectory.resolve("lc0"));
    var provider = provider(MaiaEngineProfile.MAIA_1900, lc0);

    assertThrows(ComputerEngineException.class, provider::create);
  }

  private MaiaComputerMoveEngineProvider provider(MaiaEngineProfile profile, Path executable) {
    ComputerEngineSettingsRepository repository =
        new ComputerEngineSettingsRepository() {
          @Override
          public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
            if (ComputerEngineIds.MAIA.equals(engineId)) {
              return Optional.of(new ComputerEngineSettings(ComputerEngineIds.MAIA, executable));
            }
            return Optional.empty();
          }

          @Override
          public void save(ComputerEngineSettings settings) {}

          @Override
          public void deleteByEngineId(String engineId) {}
        };
    var settingsService =
        new ComputerEngineSettingsService(
            repository, executable.toString(), temporaryDirectory.toString());
    return new MaiaComputerMoveEngineProvider(
        profile, settingsService, new FenService(), new MaiaExecutableResolver(), 4);
  }

  private Path executable(Path path) throws IOException {
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path;
  }
}
