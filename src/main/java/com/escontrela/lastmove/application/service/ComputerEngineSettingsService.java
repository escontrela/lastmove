package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Application service for reading and changing executable paths of computer opponents. */
@Service
public final class ComputerEngineSettingsService {

  private static final Duration DEFAULT_THINKING_TIME = Duration.ofMillis(500);

  private final ComputerEngineSettingsRepository repository;
  private final Path defaultSunfishExecutable;
  private final Path defaultMaiaWeightsLocation;

  public ComputerEngineSettingsService(
      ComputerEngineSettingsRepository repository,
      @Value("${lastmove.engine.sunfish.executable}") String defaultSunfishExecutable,
      @Value("${lastmove.engine.maia.models-directory:${user.home}/.local/share/maia}")
          String defaultMaiaWeightsLocation) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.defaultSunfishExecutable = parsePath(defaultSunfishExecutable, "Sunfish executable");
    this.defaultMaiaWeightsLocation = parsePath(defaultMaiaWeightsLocation, "Maia weights");
  }

  /** Returns the stored Sunfish executable or the application-configured default. */
  public ComputerEngineSettings sunfishSettings() {
    return repository
        .findByEngineId(ComputerEngineIds.SUNFISH)
        .orElseGet(
            () ->
                new ComputerEngineSettings(
                    ComputerEngineIds.SUNFISH, defaultSunfishExecutable));
  }

  /** Persists the executable selected for Sunfish. */
  public ComputerEngineSettings updateSunfishExecutable(String executablePath) {
    ComputerEngineSettings settings =
        new ComputerEngineSettings(
            ComputerEngineIds.SUNFISH, parsePath(executablePath, "Sunfish executable"));
    repository.save(settings);
    return settings;
  }

  /**
   * Returns the user-stored {@code lc0} override for Maia, if any.
   *
   * <p>An empty result means no override has been configured, so the Maia resolver falls back to
   * discovering {@code lc0} on {@code PATH} and in standard package-manager locations.
   */
  public Optional<Path> maiaExecutable() {
    return repository
        .findByEngineId(ComputerEngineIds.MAIA)
        .map(ComputerEngineSettings::executablePath);
  }

  /** Persists the {@code lc0} executable selected for the Maia profiles. */
  public ComputerEngineSettings updateMaiaExecutable(String executablePath) {
    ComputerEngineSettings settings =
        new ComputerEngineSettings(
            ComputerEngineIds.MAIA, parsePath(executablePath, "Maia executable"));
    repository.save(settings);
    return settings;
  }

  /** Removes the {@code lc0} override so Maia falls back to executable discovery. */
  public void clearMaiaExecutable() {
    repository.deleteByEngineId(ComputerEngineIds.MAIA);
  }

  /**
   * Returns the effective Maia weights location.
   *
   * <p>This is either the user-stored override or the application-configured default. It may point
   * at a directory of {@code .pb.gz} files (resolved per profile) or directly at one weights file
   * shared by every profile.
   */
  public Path maiaWeightsLocation() {
    return repository
        .findByEngineId(ComputerEngineIds.MAIA_WEIGHTS)
        .map(ComputerEngineSettings::executablePath)
        .orElse(defaultMaiaWeightsLocation);
  }

  /** Persists the Maia weights location selected by the user. */
  public ComputerEngineSettings updateMaiaWeightsLocation(String weightsLocation) {
    ComputerEngineSettings settings =
        new ComputerEngineSettings(
            ComputerEngineIds.MAIA_WEIGHTS, parsePath(weightsLocation, "Maia weights"));
    repository.save(settings);
    return settings;
  }

  /** Returns the effective thinking time for an engine, defaulting to 500 ms. */
  public Duration thinkingTime(String engineId) {
    return repository
        .findThinkingTimeMillis(engineId)
        .map(Duration::ofMillis)
        .orElse(DEFAULT_THINKING_TIME);
  }

  /** Persists the thinking time for an engine, rejecting non-positive values. */
  public Duration updateThinkingTime(String engineId, Duration thinkingTime) {
    Duration required = Objects.requireNonNull(thinkingTime, "thinkingTime must not be null");
    if (required.isZero() || required.isNegative()) {
      throw new IllegalArgumentException("Thinking time must be positive");
    }
    repository.saveThinkingTimeMillis(engineId, required.toMillis());
    return required;
  }

  /** Returns the user-selected default analysis engine, or empty to fall back to Knightshade. */
  public Optional<String> defaultAnalysisEngineId() {
    return repository.findDefaultAnalysisEngineId();
  }

  /** Persists or clears the default analysis engine, rejecting blank identifiers. */
  public void updateDefaultAnalysisEngineId(Optional<String> engineId) {
    Optional<String> required =
        Objects.requireNonNull(engineId, "engineId must not be null")
            .map(String::trim)
            .filter(value -> !value.isEmpty());
    if (required.isPresent()) {
      repository.saveDefaultAnalysisEngineId(required.orElseThrow());
    } else {
      repository.deleteDefaultAnalysisEngineId();
    }
  }

  private static Path parsePath(String value, String description) {
    String required =
        Objects.requireNonNull(value, "executablePath must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException(description + " path must not be blank");
    }
    try {
      return Path.of(required).toAbsolutePath().normalize();
    } catch (InvalidPathException exception) {
      throw new IllegalArgumentException(description + " path is invalid", exception);
    }
  }
}
