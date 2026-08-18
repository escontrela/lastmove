package com.escontrela.lastmove.application.computer;

import java.util.Optional;

/** Persistence contract for user-selected external engine executable locations. */
public interface ComputerEngineSettingsRepository {

  Optional<ComputerEngineSettings> findByEngineId(String engineId);

  void save(ComputerEngineSettings settings);

  /** Removes a stored override so the engine falls back to its configured default or discovery. */
  void deleteByEngineId(String engineId);
}
