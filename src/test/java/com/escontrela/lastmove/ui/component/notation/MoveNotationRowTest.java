package com.escontrela.lastmove.ui.component.notation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MoveNotationRowTest {

  @Test
  void groupsSequentialPliesIntoSelectableWhiteAndBlackColumns() {
    List<MoveNotationRow> rows =
        MoveNotationRow.group(
            List.of(
                entry(1, true, "e4"),
                entry(1, false, "e5"),
                entry(2, true, "Nf3"),
                entry(2, false, "Nc6")));

    assertEquals(2, rows.size());
    assertEquals("e4", rows.getFirst().whiteMove().orElseThrow().san());
    assertEquals("e5", rows.getFirst().blackMove().orElseThrow().san());
    assertEquals("Nf3", rows.getLast().whiteMove().orElseThrow().san());
    assertEquals("Nc6", rows.getLast().blackMove().orElseThrow().san());
  }

  @Test
  void supportsALineThatStartsWithBlackFromFen() {
    List<MoveNotationRow> rows =
        MoveNotationRow.group(List.of(entry(42, false, "Kh2")));

    assertEquals(42, rows.getFirst().moveNumber());
    assertTrue(rows.getFirst().whiteMove().isEmpty());
    assertEquals("Kh2", rows.getFirst().blackMove().orElseThrow().san());
  }

  @Test
  void flattensSiblingContinuationAsAnIndentedSelectableVariation() {
    MoveNotationNode knightLine =
        node(entry(2, true, "Nf3"), node(entry(2, false, "Nc6")));
    MoveNotationNode bishopVariation = node(entry(2, true, "Bc4"));
    MoveNotationNode tree =
        node(
            entry(1, true, "e4"),
            node(entry(1, false, "e5"), knightLine, bishopVariation));

    List<MoveNotationRow> rows = MoveNotationRow.flatten(List.of(tree));
    List<String> renderedMoves =
        rows.stream()
            .flatMap(
                row ->
                    Stream.concat(row.whiteMove().stream(), row.blackMove().stream()))
            .map(MoveNotationEntry::san)
            .toList();

    assertEquals(List.of("e4", "e5", "Nf3", "Bc4", "Nc6"), renderedMoves);
    MoveNotationRow variationRow =
        rows.stream()
            .filter(row -> row.whiteMove().map(move -> move.san().equals("Bc4")).orElse(false))
            .findFirst()
            .orElseThrow();
    assertEquals(1, variationRow.depth());
    assertTrue(variationRow.variationStart());
  }

  private static MoveNotationEntry entry(int moveNumber, boolean whiteMove, String san) {
    return new MoveNotationEntry(UUID.randomUUID(), moveNumber, whiteMove, san, true);
  }

  private static MoveNotationNode node(MoveNotationEntry entry, MoveNotationNode... children) {
    return new MoveNotationNode(entry, List.of(children));
  }
}
