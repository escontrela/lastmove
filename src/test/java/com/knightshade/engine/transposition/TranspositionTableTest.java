package com.knightshade.engine.transposition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.knightshade.engine.transposition.TranspositionTable.ScoreType;
import org.junit.jupiter.api.Test;

class TranspositionTableTest {

  @Test
  void retainsDeepResultsWhileReplacingShallowCollisionsInABoundedBucket() {
    var table = new TranspositionTable(4);
    table.store(1, null, 12, 42, ScoreType.EXACT);
    for (long key = 2; key < 1000; key++) {
      table.store(key, null, 1, 10, ScoreType.UPPER_BOUND);
    }
    assertEquals(42, table.probe(1).score());
    assertNotNull(table.probe(999));
    table.clear();
    assertNull(table.probe(1));
    assertNull(table.probe(999));
  }

  @Test
  void acceptsZeroHashAndRejectsInvalidCapacities() {
    var table = new TranspositionTable(1);
    table.store(0, null, 1, 22, ScoreType.EXACT);
    assertEquals(22, table.probe(0).score());
    assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(0));
    assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(Integer.MAX_VALUE));
  }

  @Test
  void doesNotReplaceADeepEntryWithAShallowQuiescenceResultForTheSamePosition() {
    TranspositionTable table = new TranspositionTable(16);
    long key = 42L;
    table.store(key, null, 6, 120, ScoreType.LOWER_BOUND);

    table.store(key, null, 0, 80, ScoreType.EXACT);

    assertEquals(6, table.probe(key).depth());
    assertEquals(120, table.probe(key).score());
    assertEquals(ScoreType.LOWER_BOUND, table.probe(key).type());
  }
}
