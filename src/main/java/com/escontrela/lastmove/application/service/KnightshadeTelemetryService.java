package com.escontrela.lastmove.application.service;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Application-side bridge for optional Knightshade telemetry. */
@Service
public final class KnightshadeTelemetryService {
  private final CopyOnWriteArrayList<Consumer<SearchTelemetrySnapshot>> listeners =
      new CopyOnWriteArrayList<>();
  private volatile boolean enabled;
  private final CopyOnWriteArrayList<SearchTelemetrySnapshot> samples = new CopyOnWriteArrayList<>();
  private volatile Instant sessionStartedAt;

  public boolean isEnabled() { return enabled; }

  public void setEnabled(boolean enabled) {
    boolean wasEnabled = this.enabled;
    this.enabled = enabled;
    if (enabled && !wasEnabled) beginSession();
  }

  /** Starts a new game session, discarding samples from the previous game. */
  public void beginSession() {
    samples.clear();
    sessionStartedAt = Instant.now();
  }

  public List<SearchTelemetrySnapshot> samples() { return List.copyOf(new ArrayList<>(samples)); }
  public Instant sessionStartedAt() { return sessionStartedAt; }

  public Runnable subscribe(Consumer<SearchTelemetrySnapshot> listener) {
    Consumer<SearchTelemetrySnapshot> required = Objects.requireNonNull(listener);
    listeners.add(required);
    return () -> listeners.remove(required);
  }

  /** Delivers snapshots only while monitoring is enabled. */
  public void publish(SearchTelemetrySnapshot snapshot) {
    if (!enabled) return;
    samples.add(snapshot);
    listeners.forEach(listener -> {
      try { listener.accept(snapshot); } catch (RuntimeException ignored) { }
    });
  }
}
