package com.escontrela.lastmove.infrastructure.engine;

import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.computer.PonderSettings;
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
  private static final String DEFAULT_ANALYSIS_ENGINE_KEY = "analysis.default-engine";
  private static final String PONDER_ENABLED_KEY = "knightshade.ponder.enabled";
  private static final String PONDER_WORKER_KEY = "knightshade.ponder.worker-enabled";
  private static final String PONDER_PREDICTION_DEPTH_KEY = "knightshade.ponder.prediction-depth";
  private static final String PONDER_PREDICTION_BUDGET_KEY = "knightshade.ponder.prediction-budget-ms";
  private static final String PONDER_CONTINUATION_DEPTH_KEY = "knightshade.ponder.continuation-depth";
  private static final String PONDER_CONTINUATION_BUDGET_KEY = "knightshade.ponder.continuation-budget-ms";
  private static final String KNIGHTSHADE_BITBOARDS_ENABLED_KEY = "knightshade.bitboards.enabled";

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

  @Override
  public Optional<String> findDefaultAnalysisEngineId() {
    String value = preferences.get(DEFAULT_ANALYSIS_ENGINE_KEY, null);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  @Override
  public void saveDefaultAnalysisEngineId(String engineId) {
    preferences.put(DEFAULT_ANALYSIS_ENGINE_KEY, requireEngineId(engineId));
  }

  @Override
  public void deleteDefaultAnalysisEngineId() {
    preferences.remove(DEFAULT_ANALYSIS_ENGINE_KEY);
  }

  @Override
  public PonderSettings findPonderSettings() {
    PonderSettings defaults = PonderSettings.defaults();
    try {
      return new PonderSettings(
          preferences.getBoolean(PONDER_ENABLED_KEY, defaults.enabled()),
          preferences.getBoolean(PONDER_WORKER_KEY, defaults.speculativeWorkerEnabled()),
          preferences.getInt(PONDER_PREDICTION_DEPTH_KEY, defaults.predictionDepth()),
          java.time.Duration.ofMillis(preferences.getLong(PONDER_PREDICTION_BUDGET_KEY,
              defaults.predictionBudget().toMillis())),
          preferences.getInt(PONDER_CONTINUATION_DEPTH_KEY, defaults.continuationDepth()),
          java.time.Duration.ofMillis(preferences.getLong(PONDER_CONTINUATION_BUDGET_KEY,
              defaults.continuationBudget().toMillis())));
    } catch (IllegalArgumentException exception) {
      return defaults;
    }
  }

  @Override
  public void savePonderSettings(PonderSettings settings) {
    PonderSettings value = Objects.requireNonNull(settings, "settings must not be null");
    preferences.putBoolean(PONDER_ENABLED_KEY, value.enabled());
    preferences.putBoolean(PONDER_WORKER_KEY, value.speculativeWorkerEnabled());
    preferences.putInt(PONDER_PREDICTION_DEPTH_KEY, value.predictionDepth());
    preferences.putLong(PONDER_PREDICTION_BUDGET_KEY, value.predictionBudget().toMillis());
    preferences.putInt(PONDER_CONTINUATION_DEPTH_KEY, value.continuationDepth());
    preferences.putLong(PONDER_CONTINUATION_BUDGET_KEY, value.continuationBudget().toMillis());
  }

  @Override
  public boolean findKnightshadeBitboardsEnabled() {
    return preferences.getBoolean(KNIGHTSHADE_BITBOARDS_ENABLED_KEY, false);
  }

  @Override
  public void saveKnightshadeBitboardsEnabled(boolean enabled) {
    preferences.putBoolean(KNIGHTSHADE_BITBOARDS_ENABLED_KEY, enabled);
  }

  private String requireEngineId(String value) {
    String required = Objects.requireNonNull(value, "engineId must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("engineId must not be blank");
    }
    return required;
  }
}
