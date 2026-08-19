package com.escontrela.lastmove.infrastructure.engine.maia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaiaExecutableResolverTest {

  @TempDir Path temporaryDirectory;

  @Test
  void resolvesTheConfiguredExecutableAndWeightsFile() throws IOException {
    Path lc0 = executable(temporaryDirectory.resolve("lc0"));
    Path weights = weights("maia-1100.pb.gz");

    MaiaRuntimeDetails runtime =
        new MaiaExecutableResolver().resolve(lc0, temporaryDirectory, MaiaEngineProfile.MAIA_1100);

    assertEquals(lc0.toAbsolutePath(), runtime.executable());
    assertEquals(weights.toAbsolutePath(), runtime.weightsFile());
  }

  @Test
  void rejectsAnExecutableThatExistsButIsNotRunnable() throws IOException {
    Path lc0 = temporaryDirectory.resolve("lc0");
    Files.writeString(lc0, "not executable");
    weights("maia-1100.pb.gz");

    assertThrows(
        ComputerEngineException.class,
        () ->
            new MaiaExecutableResolver()
                .resolve(lc0, temporaryDirectory, MaiaEngineProfile.MAIA_1100));
  }

  @Test
  void rejectsAMissingWeightsFile() throws IOException {
    Path lc0 = executable(temporaryDirectory.resolve("lc0"));

    assertThrows(
        ComputerEngineException.class,
        () ->
            new MaiaExecutableResolver()
                .resolve(lc0, temporaryDirectory, MaiaEngineProfile.MAIA_1900));
  }

  @Test
  void acceptsASingleWeightsFileSharedAcrossProfiles() throws IOException {
    Path lc0 = executable(temporaryDirectory.resolve("lc0"));
    Path weightsFile = weights("42850.pb.gz");

    MaiaRuntimeDetails runtime =
        new MaiaExecutableResolver()
            .resolve(lc0, weightsFile, MaiaEngineProfile.MAIA_1700);

    assertEquals(weightsFile.toAbsolutePath(), runtime.weightsFile());
  }

  @Test
  void discoversTheExecutableFromTheCandidateList() throws IOException {
    Path discovered = executable(temporaryDirectory.resolve("lc0"));
    weights("maia-1500.pb.gz");

    MaiaRuntimeDetails runtime =
        new MaiaExecutableResolver(List.of(discovered))
            .resolve(null, temporaryDirectory, MaiaEngineProfile.MAIA_1500);

    assertEquals(discovered.toAbsolutePath(), runtime.executable());
  }

  @Test
  void failsWhenNoExecutableCandidateExists() throws IOException {
    weights("maia-1100.pb.gz");

    assertThrows(
        ComputerEngineException.class,
        () ->
            new MaiaExecutableResolver(List.of())
                .resolve(null, temporaryDirectory, MaiaEngineProfile.MAIA_1100));
  }

  private Path weights(String fileName) throws IOException {
    Path weights = temporaryDirectory.resolve(fileName);
    Files.writeString(weights, "weights");
    return weights;
  }

  private Path executable(Path path) throws IOException {
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path;
  }
}
