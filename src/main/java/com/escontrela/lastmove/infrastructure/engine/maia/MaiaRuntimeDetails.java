package com.escontrela.lastmove.infrastructure.engine.maia;

import java.nio.file.Path;
import java.util.Objects;

/** Resolved {@code lc0} executable and the Maia weights file it must load. */
public record MaiaRuntimeDetails(Path executable, Path weightsFile) {

  public MaiaRuntimeDetails {
    executable = normalize(executable, "executable");
    weightsFile = normalize(weightsFile, "weightsFile");
  }

  private static Path normalize(Path path, String field) {
    Path normalized = Objects.requireNonNull(path, field + " must not be null").normalize();
    if (!normalized.isAbsolute()) {
      throw new IllegalArgumentException(field + " must be an absolute path");
    }
    return normalized;
  }
}
