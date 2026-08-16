package com.escontrela.lastmove.application.computer;

import java.nio.file.Path;
import java.util.Objects;

/** Persistable executable location for one configured external computer engine. */
public record ComputerEngineSettings(String engineId, Path executablePath) {

  public ComputerEngineSettings {
    engineId = Objects.requireNonNull(engineId, "engineId must not be null").trim();
    if (engineId.isEmpty()) {
      throw new IllegalArgumentException("engineId must not be blank");
    }
    executablePath =
        Objects.requireNonNull(executablePath, "executablePath must not be null")
            .toAbsolutePath()
            .normalize();
  }
}
