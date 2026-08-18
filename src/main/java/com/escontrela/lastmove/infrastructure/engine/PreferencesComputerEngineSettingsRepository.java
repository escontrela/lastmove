package com.escontrela.lastmove.infrastructure.engine;

import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import org.springframework.stereotype.Repository;

/** Java Preferences implementation retaining engine executable paths between desktop sessions. */
@Repository
public class PreferencesComputerEngineSettingsRepository
    implements ComputerEngineSettingsRepository {

  private static final String EXECUTABLE_SUFFIX = ".executable";
  private static final String THINKING_TIME_SUFFIX = ".thinking-time";

  private final Preferences preferences =
      Preferences.userNodeForPackage(PreferencesComputerEngineSettingsRepository.class)
          .node("computer-engines");

  @Override
  public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
    String requiredId = requireEngineId(engineId);
    String executable = preferences.get(requiredId + EXECUTABLE_SUFFIX, null);
    if (executable == null || executable.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(new ComputerEngineSettings(requiredId, Path.of(executable)));
    } catch (InvalidPathException exception) {
      return Optional.empty();
    }
  }

  @Override
  public void save(ComputerEngineSettings settings) {
    ComputerEngineSettings required =
        Objects.requireNonNull(settings, "settings must not be null");
    preferences.put(
        requireEngineId(required.engineId()) + EXECUTABLE_SUFFIX,
        required.executablePath().toString());
  }

  @Override
  public void deleteByEngineId(String engineId) {
    preferences.remove(requireEngineId(engineId) + EXECUTABLE_SUFFIX);
  }

  @Override
  public Optional<Long> findThinkingTimeMillis(String engineId) {
    String value = preferences.get(requireEngineId(engineId) + THINKING_TIME_SUFFIX, null);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(value.trim()));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  @Override
  public void saveThinkingTimeMillis(String engineId, long thinkingTimeMillis) {
    preferences.put(
        requireEngineId(engineId) + THINKING_TIME_SUFFIX, Long.toString(thinkingTimeMillis));
  }

  private String requireEngineId(String value) {
    String required = Objects.requireNonNull(value, "engineId must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("engineId must not be blank");
    }
    return required;
  }
}
