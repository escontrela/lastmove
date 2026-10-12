package com.knightshade.engine.transposition;

import com.knightshade.engine.board.Move;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Four-way set-associative table, safe to share among workers of one search request.
 * Keys and results are published together as immutable entries. A contended write may be
 * discarded: this is a cache, and searching again is preferable to blocking search workers.
 */
public final class TranspositionTable {
  public enum ScoreType { EXACT, LOWER_BOUND, UPPER_BOUND }

  public record Entry(long key, Move move, int depth, int score, ScoreType type) {}

  private static final int DEFAULT_CAPACITY = 1 << 18;
  private static final int BUCKET_SIZE = 4;
  private final AtomicReferenceArray<Entry> entries;
  private final int bucketMask;

  public TranspositionTable() {
    this(DEFAULT_CAPACITY);
  }

  public TranspositionTable(int capacity) {
    if (capacity < 1 || capacity > (1 << 26)) {
      throw new IllegalArgumentException("capacity must be between 1 and 2^26");
    }
    int size = BUCKET_SIZE;
    while (size < capacity) {
      size <<= 1;
    }
    entries = new AtomicReferenceArray<>(size);
    bucketMask = size / BUCKET_SIZE - 1;
  }

  public Entry probe(long key) {
    int start = bucket(key);
    Entry best = null;
    for (int i = start; i < start + BUCKET_SIZE; i++) {
      Entry current = entries.get(i);
      // Concurrent insertions can leave duplicate keys in a bucket. Prefer the deepest result.
      if (current != null && current.key() == key
          && (best == null || current.depth() > best.depth()
              || current.depth() == best.depth() && current.type() == ScoreType.EXACT)) {
        best = current;
      }
    }
    return best;
  }

  public void store(long key, Move move, int depth, int score, ScoreType type) {
    int start = bucket(key);
    int replacement = -1;
    Entry victim = null;
    for (int i = start; i < start + BUCKET_SIZE; i++) {
      Entry current = entries.get(i);
      if (current != null && current.key() == key) {
        if (current.depth() > depth
            || current.depth() == depth && current.type() == ScoreType.EXACT
                && type != ScoreType.EXACT) {
          return;
        }
        entries.compareAndSet(i, current,
            new Entry(key, move == null ? current.move() : move, depth, score, type));
        return;
      }
      if (replacement < 0 || victim != null && (current == null || quality(current) < quality(victim))) {
        replacement = i;
        victim = current;
      }
    }
    if (victim != null && quality(victim) > depth + 2) {
      return;
    }
    // Never overwrite a replacement published by another worker after our bucket scan.
    entries.compareAndSet(replacement, victim, new Entry(key, move, depth, score, type));
  }

  private int quality(Entry entry) {
    return entry.depth() + (entry.type() == ScoreType.EXACT ? 2 : 0);
  }

  private int bucket(long key) {
    return ((int) key & bucketMask) * BUCKET_SIZE;
  }

  /** Clear only while no worker is using this table, before starting a new search context. */
  public void clear() {
    for (int i = 0; i < entries.length(); i++) entries.set(i, null);
  }
}
