package com.escontrela.lastmove.infrastructure.engine.maia;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code lc0} executable and the weights file that make one Maia profile launchable.
 *
 * <p>Maia is Leela Chess Zero running a human-style network. The executable is {@code lc0}, and the
 * "engine" only differs between profiles through the {@code --weights} argument. When the user has
 * not stored an explicit override, the executable is discovered on {@code PATH} and in the usual
 * package-manager locations so a standard installation works without further configuration.
 */
@Component
public final class MaiaExecutableResolver {

  private static final String EXECUTABLE_NAME = "lc0";

  private final List<Path> candidateExecutables;

  public MaiaExecutableResolver() {
    this(systemCandidateExecutables());
  }

  /** Test-only entry point supplying the full candidate list instead of probing the system. */
  MaiaExecutableResolver(List<Path> candidateExecutables) {
    this.candidateExecutables =
        List.copyOf(
            Objects.requireNonNull(candidateExecutables, "candidateExecutables must not be null"));
  }

  /** Validates the configured or discovered executable and the profile weights file. */
  public MaiaRuntimeDetails resolve(
      Path configuredExecutable, Path weightsLocation, MaiaEngineProfile profile) {
    Path executable = resolveExecutable(configuredExecutable);
    Path weights = resolveWeights(weightsLocation, profile.weightsFileName());
    return new MaiaRuntimeDetails(executable, weights);
  }

  private Path resolveExecutable(Path configuredExecutable) {
    if (configuredExecutable != null) {
      Path configured = configuredExecutable.toAbsolutePath().normalize();
      if (Files.isRegularFile(configured)) {
        if (!Files.isExecutable(configured)) {
          throw new ComputerEngineException("Maia executable is not executable: " + configured);
        }
        return configured;
      }
    }
    return discoverExecutable(configuredExecutable);
  }

  private Path discoverExecutable(Path configuredExecutable) {
    for (Path candidate : candidateExecutables) {
      if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
        return candidate.toAbsolutePath().normalize();
      }
    }
    String hint =
        configuredExecutable == null
            ? "Configure a Maia executable or install lc0 on PATH."
            : "The configured Maia executable was not found: " + configuredExecutable;
    throw new ComputerEngineException("Maia (lc0) executable was not found. " + hint);
  }

  private static List<Path> systemCandidateExecutables() {
    List<Path> candidates = new ArrayList<>();
    String pathValue = System.getenv("PATH");
    if (pathValue != null) {
      for (String entry : pathValue.split(File.pathSeparator)) {
        if (!entry.isBlank()) {
          candidates.add(Path.of(entry, EXECUTABLE_NAME));
        }
      }
    }
    String userHome = System.getProperty("user.home");
    if (userHome != null) {
      candidates.add(Path.of(userHome, ".local", "bin", EXECUTABLE_NAME));
    }
    candidates.add(Path.of("/opt/homebrew/bin", EXECUTABLE_NAME));
    candidates.add(Path.of("/usr/local/bin", EXECUTABLE_NAME));
    candidates.add(Path.of("/usr/bin", EXECUTABLE_NAME));
    return candidates;
  }

  private Path resolveWeights(Path weightsLocation, String weightsFileName) {
    Path location =
        Objects.requireNonNull(weightsLocation, "weightsLocation must not be null")
            .toAbsolutePath()
            .normalize();
    if (Files.isRegularFile(location)) {
      return location;
    }
    if (!Files.isDirectory(location)) {
      throw new ComputerEngineException("Maia weights location was not found: " + location);
    }
    Path weights = location.resolve(weightsFileName);
    if (!Files.isRegularFile(weights)) {
      throw new ComputerEngineException(
          "Maia weights file was not found: " + weights + " (in " + location + ")");
    }
    return weights;
  }
}
