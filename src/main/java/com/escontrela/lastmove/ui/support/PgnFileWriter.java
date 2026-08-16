package com.escontrela.lastmove.ui.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Writes application-produced PGN text to a file selected by the presentation layer. */
@Component
public final class PgnFileWriter {

  /** Replaces the selected file with UTF-8 PGN text and returns its absolute path. */
  public Path write(File file, String pgn) {
    Path path = Objects.requireNonNull(file, "file must not be null").toPath().toAbsolutePath();
    try {
      Files.writeString(
          path,
          Objects.requireNonNull(pgn, "pgn must not be null"),
          StandardCharsets.UTF_8);
      return path;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to export PGN to " + path, exception);
    }
  }
}
