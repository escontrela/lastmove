package com.escontrela.lastmove.infrastructure.engine.sunfish;

import java.nio.file.Path;
import java.util.Objects;

/** Resolved executable wrapper and Python interpreter used to launch Sunfish UCI. */
public record SunfishRuntimeDetails(Path executable, Path interpreter, String shebang) {

  public SunfishRuntimeDetails {
    executable = normalize(executable, "executable");
    interpreter = normalize(interpreter, "interpreter");
    shebang = Objects.requireNonNull(shebang, "shebang must not be null").trim();
    if (shebang.isEmpty()) {
      throw new IllegalArgumentException("shebang must not be blank");
    }
  }

  private static Path normalize(Path path, String field) {
    Path normalized = Objects.requireNonNull(path, field + " must not be null").normalize();
    if (!normalized.isAbsolute()) {
      throw new IllegalArgumentException(field + " must be an absolute path");
    }
    return normalized;
  }
}
