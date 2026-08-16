package com.escontrela.lastmove.infrastructure.engine.sunfish;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Validates the configured Sunfish wrapper and resolves its shebang Python interpreter. */
@Component
public final class SunfishExecutableResolver {

  /** Resolves a launchable wrapper and fails with a user-facing diagnosis when it is invalid. */
  public SunfishRuntimeDetails resolve(Path configuredExecutable) {
    Path executable =
        Objects.requireNonNull(configuredExecutable, "configuredExecutable must not be null")
            .toAbsolutePath()
            .normalize();
    requireExecutable(executable, "Sunfish executable");

    String shebang = readShebang(executable);
    Path interpreter = interpreterFrom(shebang, executable);
    requireExecutable(interpreter, "Python interpreter declared by Sunfish");
    return new SunfishRuntimeDetails(executable, interpreter, shebang);
  }

  private static String readShebang(Path executable) {
    try (BufferedReader reader = Files.newBufferedReader(executable, StandardCharsets.UTF_8)) {
      String firstLine = reader.readLine();
      if (firstLine == null || !firstLine.startsWith("#!")) {
        throw new ComputerEngineException(
            "Sunfish executable does not declare a Python interpreter: " + executable);
      }
      return firstLine;
    } catch (IOException exception) {
      throw new ComputerEngineException("Could not read Sunfish executable: " + executable, exception);
    }
  }

  private static Path interpreterFrom(String shebang, Path executable) {
    String declaration = shebang.substring(2).trim();
    if (declaration.isEmpty()) {
      throw new ComputerEngineException(
          "Sunfish executable has an empty interpreter declaration: " + executable);
    }
    String interpreterToken = declaration.split("\\s+", 2)[0];
    Path interpreter;
    try {
      interpreter = Path.of(interpreterToken).normalize();
    } catch (RuntimeException exception) {
      throw new ComputerEngineException(
          "Sunfish declares an invalid Python interpreter: " + interpreterToken, exception);
    }
    if (!interpreter.isAbsolute()) {
      throw new ComputerEngineException(
          "Sunfish must declare an absolute Python interpreter: " + interpreterToken);
    }
    return interpreter;
  }

  private static void requireExecutable(Path path, String description) {
    if (!Files.isRegularFile(path)) {
      throw new ComputerEngineException(description + " was not found: " + path);
    }
    if (!Files.isExecutable(path)) {
      throw new ComputerEngineException(description + " is not executable: " + path);
    }
  }
}
