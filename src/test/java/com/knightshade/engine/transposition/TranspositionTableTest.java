package com.knightshade.engine.transposition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.knightshade.engine.transposition.TranspositionTable.ScoreType;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TranspositionTableTest {

  @Test
  @Timeout(10)
  void concurrentCollisionsNeverMixAKeyWithAnotherPositionsScore() throws Exception {
    var table = new TranspositionTable(4);
    var ready = new CountDownLatch(4);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<Future<?>>();
      for (int worker = 0; worker < 4; worker++) {
        int id = worker;
        futures.add(executor.submit(() -> {
          ready.countDown();
          assertTrue(start.await(5, TimeUnit.SECONDS));
          for (int iteration = 0; iteration < 10000; iteration++) {
            long key = id * 10000L + iteration;
            table.store(key, null, 8, (int) key, ScoreType.EXACT);
            for (long probe : new long[] {key, key - 1, iteration}) {
              var entry = table.probe(probe);
              if (entry != null) {
                assertEquals(probe, entry.key());
                assertEquals((int) probe, entry.score());
                assertEquals(8, entry.depth());
                assertEquals(ScoreType.EXACT, entry.type());
              }
            }
          }
          return null;
        }));
      }
      try {
        assertTrue(ready.await(5, TimeUnit.SECONDS));
      } finally {
        start.countDown();
      }
      for (var future : futures) future.get(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void preservesAnExactResultAgainstAnEqualDepthBound() {
    var table = new TranspositionTable(4);
    table.store(7, null, 6, 120, ScoreType.EXACT);
    table.store(7, null, 6, 80, ScoreType.UPPER_BOUND);
    assertEquals(120, table.probe(7).score());
    assertEquals(ScoreType.EXACT, table.probe(7).type());
  }

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
