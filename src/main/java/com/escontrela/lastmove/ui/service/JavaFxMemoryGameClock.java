package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.training.memory.MemoryGameCancellable;
import com.escontrela.lastmove.application.training.memory.MemoryGameClock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

/** Monotonic JavaFX-facing clock adapter; scheduling remains independent from the domain aggregate. */
@Component
public final class JavaFxMemoryGameClock implements MemoryGameClock {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "memory-game-clock");
    thread.setDaemon(true);
    return thread;
  });
  private final Set<ScheduledFuture<?>> futures = ConcurrentHashMap.newKeySet();
  private volatile long startedAt = System.nanoTime();

  @Override public void reset() {
    cancelAll();
    startedAt = System.nanoTime();
  }

  @Override public Duration elapsed() {
    return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
  }

  @Override public MemoryGameCancellable schedule(Duration delay, Runnable callback) {
    Duration requiredDelay = Objects.requireNonNull(delay, "delay must not be null");
    Objects.requireNonNull(callback, "callback must not be null");
    if (requiredDelay.isNegative()) throw new IllegalArgumentException("delay must not be negative");
    ScheduledFuture<?> future = scheduler.schedule(callback, requiredDelay.toNanos(), TimeUnit.NANOSECONDS);
    futures.add(future);
    return () -> { future.cancel(false); futures.remove(future); };
  }

  @Override public void cancelAll() {
    futures.forEach(future -> future.cancel(false));
    futures.clear();
  }

  @PreDestroy
  void shutdown() {
    cancelAll();
    scheduler.shutdownNow();
  }
}
