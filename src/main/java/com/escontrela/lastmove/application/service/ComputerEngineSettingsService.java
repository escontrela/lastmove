package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Application service for reading and changing executable paths of computer opponents. */
@Service
public final class ComputerEngineSettingsService {

  private final ComputerEngineSettingsRepository repository;
  private final Path defaultSunfishExecutable;

  public ComputerEngineSettingsService(
      ComputerEngineSettingsRepository repository,
      @Value("${lastmove.engine.sunfish.executable}") String defaultSunfishExecutable) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.defaultSunfishExecutable = parsePath(defaultSunfishExecutable);
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
        new ComputerEngineSettings(ComputerEngineIds.SUNFISH, parsePath(executablePath));
    repository.save(settings);
    return settings;
  }

  private static Path parsePath(String value) {
    String required =
        Objects.requireNonNull(value, "executablePath must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("Sunfish executable path must not be blank");
    }
    try {
      return Path.of(required).toAbsolutePath().normalize();
    } catch (InvalidPathException exception) {
      throw new IllegalArgumentException("Sunfish executable path is invalid", exception);
    }
  }
}
