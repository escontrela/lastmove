package com.escontrela.lastmove.ui.component.notation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveNotationRowTest {

  @Test
  void groupsSequentialPliesIntoSelectableWhiteAndBlackColumns() {
    List<MoveNotationRow> rows =
        MoveNotationRow.group(
            List.of(
                new MoveNotationEntry(0, 1, true, "e4"),
                new MoveNotationEntry(1, 1, false, "e5"),
                new MoveNotationEntry(2, 2, true, "Nf3"),
                new MoveNotationEntry(3, 2, false, "Nc6")));

    assertEquals(2, rows.size());
    assertEquals("e4", rows.getFirst().whiteMove().orElseThrow().san());
    assertEquals("e5", rows.getFirst().blackMove().orElseThrow().san());
    assertEquals(2, rows.getLast().whiteMove().orElseThrow().plyIndex());
    assertEquals("Nc6", rows.getLast().blackMove().orElseThrow().san());
  }

  @Test
  void supportsALineThatStartsWithBlackFromFen() {
    List<MoveNotationRow> rows =
        MoveNotationRow.group(List.of(new MoveNotationEntry(0, 42, false, "Kh2")));

    assertEquals(42, rows.getFirst().moveNumber());
    assertTrue(rows.getFirst().whiteMove().isEmpty());
    assertEquals("Kh2", rows.getFirst().blackMove().orElseThrow().san());
  }
}
