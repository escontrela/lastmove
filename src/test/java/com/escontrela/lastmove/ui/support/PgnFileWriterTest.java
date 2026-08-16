package com.escontrela.lastmove.ui.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PgnFileWriterTest {

  @Test
  void writesTheExportAsUtf8(@TempDir java.nio.file.Path directory) throws Exception {
    var destination = directory.resolve("partida.pgn");

    var written = new PgnFileWriter().write(destination.toFile(), "[Event \"España\"]\n\n*\n");

    assertEquals(
        "[Event \"España\"]\n\n*\n", Files.readString(written, StandardCharsets.UTF_8));
  }
}
