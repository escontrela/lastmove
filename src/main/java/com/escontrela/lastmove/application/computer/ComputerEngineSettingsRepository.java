package com.escontrela.lastmove.application.computer;

import java.util.Optional;

/** Persistence contract for user-selected external engine executable locations. */
public interface ComputerEngineSettingsRepository {

  Optional<ComputerEngineSettings> findByEngineId(String engineId);

  void save(ComputerEngineSettings settings);
}
