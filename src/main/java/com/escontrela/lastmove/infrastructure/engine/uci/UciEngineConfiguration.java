package com.escontrela.lastmove.infrastructure.engine.uci;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Process and timeout configuration for one executable Universal Chess Interface engine.
 *
 * <p>The command is represented as separate arguments so callers never need shell parsing. An
 * empty working directory uses the application process directory.
 */
public record UciEngineConfiguration(
    ComputerEngineDescriptor descriptor,
    List<String> command,
    Optional<Path> workingDirectory,
    Map<String, String> environment,
    Duration startupTimeout,
    Duration searchResponseTimeout,
    Duration shutdownTimeout) {

  public UciEngineConfiguration {
    descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
    if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("command must contain non-blank arguments");
    }
    workingDirectory =
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null")
            .map(Path::toAbsolutePath)
            .map(Path::normalize);
    workingDirectory.ifPresent(
        path -> {
          if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("workingDirectory must be an existing directory");
          }
        });
    environment = Map.copyOf(Objects.requireNonNull(environment, "environment must not be null"));
    if (environment.entrySet().stream()
        .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null)) {
      throw new IllegalArgumentException("environment must contain valid names and values");
    }
    startupTimeout = requirePositive(startupTimeout, "startupTimeout");
    searchResponseTimeout = requirePositive(searchResponseTimeout, "searchResponseTimeout");
    shutdownTimeout = requirePositive(shutdownTimeout, "shutdownTimeout");
  }

  /** Creates a configuration with no directory/environment overrides and desktop-safe timeouts. */
  public static UciEngineConfiguration of(
      ComputerEngineDescriptor descriptor, List<String> command) {
    return new UciEngineConfiguration(
        descriptor,
        command,
        Optional.empty(),
        Map.of(),
        Duration.ofSeconds(5),
        Duration.ofSeconds(2),
        Duration.ofSeconds(2));
  }

  private static Duration requirePositive(Duration value, String field) {
    Duration required = Objects.requireNonNull(value, field + " must not be null");
    if (required.isZero() || required.isNegative()) {
      throw new IllegalArgumentException(field + " must be positive");
    }
    return required;
  }
}
