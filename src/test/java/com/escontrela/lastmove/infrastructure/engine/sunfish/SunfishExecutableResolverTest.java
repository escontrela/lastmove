package com.escontrela.lastmove.infrastructure.engine.sunfish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.computer.ComputerEngineException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SunfishExecutableResolverTest {

  @TempDir Path temporaryDirectory;

  @Test
  void resolvesTheAbsoluteInterpreterDeclaredByTheWrapper() throws IOException {
    Path interpreter = Path.of(System.getProperty("java.home"), "bin", "java");
    Path wrapper = executableWrapper("#!" + interpreter);

    SunfishRuntimeDetails runtime = new SunfishExecutableResolver().resolve(wrapper);

    assertEquals(wrapper.toAbsolutePath(), runtime.executable());
    assertEquals(interpreter, runtime.interpreter());
  }

  @Test
  void rejectsAWrapperWhoseInterpreterDoesNotExist() throws IOException {
    Path wrapper = executableWrapper("#!/missing/lastmove/python3");

    assertThrows(
        ComputerEngineException.class, () -> new SunfishExecutableResolver().resolve(wrapper));
  }

  @Test
  void rejectsAFileWithoutAShebang() throws IOException {
    Path wrapper = executableWrapper("not a script");

    assertThrows(
        ComputerEngineException.class, () -> new SunfishExecutableResolver().resolve(wrapper));
  }

  private Path executableWrapper(String firstLine) throws IOException {
    Path wrapper = temporaryDirectory.resolve("sunfish-uci");
    Files.writeString(wrapper, firstLine + System.lineSeparator());
    wrapper.toFile().setExecutable(true);
    return wrapper;
  }
}
