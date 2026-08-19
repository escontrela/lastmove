package com.escontrela.lastmove.application.computer;

import java.util.Optional;

/** Persistence contract for user-selected external engine executable locations and thinking time. */
public interface ComputerEngineSettingsRepository {

  Optional<ComputerEngineSettings> findByEngineId(String engineId);

  void save(ComputerEngineSettings settings);

  /** Removes a stored override so the engine falls back to its configured default or discovery. */
  void deleteByEngineId(String engineId);

  /** Returns the stored per-engine thinking time in milliseconds, if any. */
  default Optional<Long> findThinkingTimeMillis(String engineId) {
    return Optional.empty();
  }

  /** Persists the per-engine thinking time in milliseconds. */
  default void saveThinkingTimeMillis(String engineId, long thinkingTimeMillis) {
    // Not persisted by default; repositories that retain thinking time override this method.
  }
}
