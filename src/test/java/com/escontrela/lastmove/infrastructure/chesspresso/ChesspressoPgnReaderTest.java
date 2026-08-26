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

  @Test
  void retainsDeepNestedVariationsFromALichessStylePgn() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst(
                "[Event \"Lichess\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 "
                    + "(3... Nd4 4. Nxd4 (4. Nxe5? Qg5 5. Nxf7 (5. Bxf7+ Kd8))) "
                    + "(3... Bc5 4. c3 Nf6) 4. d3 *");

    var bc4 =
        imported.rootVariations().getFirst().variations().getFirst().variations().getFirst()
            .variations().getFirst().variations().getFirst();
    assertEquals("Bc4", bc4.execution().move().orElseThrow().san().getValue());
    assertEquals(
        java.util.List.of("Nf6", "Nd4", "Bc5"),
        bc4.variations().stream()
            .map(ply -> ply.execution().move().orElseThrow().san().getValue())
            .toList());
    var nd4 = bc4.variations().get(1);
    assertEquals(
        java.util.List.of("Nxd4", "Nxe5"),
        nd4.variations().stream()
            .map(ply -> ply.execution().move().orElseThrow().san().getValue())
            .toList());
    assertEquals("Qg5", nd4.variations().get(1).variations().getFirst().execution().move().orElseThrow().san().getValue());
  }

  @Test
  void importsMoveCommentsAlongsideTheirSan() throws Exception {
    var imported =
        new ChesspressoPgnReader()
            .readImportedFirst(
                "[Event \"Comment\"]\n\n1. e4 {King pawn opening} e5 {King pawn game} *");

    assertEquals("King pawn opening", imported.rootVariations().getFirst().comment());
    assertEquals("King pawn game", imported.rootVariations().getFirst().variations().getFirst().comment());
  }

}
