package com.escontrela.lastmove.infrastructure.chesspresso;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChesspressoPgnReaderTest {

  @Test
  void readsMainLineAndAlternativeLinesIntoAnEngineNeutralTree() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst("[Event \"Variation\"]\n\n1. e4 e5 2. Nf3 (2. Bc4) Nc6 *");

    assertEquals(1, imported.rootVariations().size());
    assertEquals("e4", imported.rootVariations().getFirst().execution().move().orElseThrow().san().getValue());
    var e5 = imported.rootVariations().getFirst().variations().getFirst();
    assertEquals("e5", e5.execution().move().orElseThrow().san().getValue());
    assertEquals(2, e5.variations().size());
    assertEquals("Nf3", e5.variations().getFirst().execution().move().orElseThrow().san().getValue());
    assertEquals("Bc4", e5.variations().get(1).execution().move().orElseThrow().san().getValue());
  }
}
