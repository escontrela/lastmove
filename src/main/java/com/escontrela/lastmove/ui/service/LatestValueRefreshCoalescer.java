package com.escontrela.lastmove.ui.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Keeps only the newest pending UI value and guarantees at most one queued refresh callback. */
final class LatestValueRefreshCoalescer<T> {
  private final AtomicReference<T> latest = new AtomicReference<>();
  private final AtomicBoolean scheduled = new AtomicBoolean();

  void offer(T value, Consumer<Runnable> dispatcher, Runnable refresh) {
    latest.set(Objects.requireNonNull(value));
    scheduleIfNeeded(dispatcher, refresh);
  }

  T latest() { return latest.get(); }

  void finished(T rendered, Consumer<Runnable> dispatcher, Runnable refresh) {
    scheduled.set(false);
    if (latest.get() != rendered) scheduleIfNeeded(dispatcher, refresh);
  }

  private void scheduleIfNeeded(Consumer<Runnable> dispatcher, Runnable refresh) {
    if (scheduled.compareAndSet(false, true)) dispatcher.accept(refresh);
  }
}
