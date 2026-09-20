package com.escontrela.lastmove.application.service;

import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.Set;
import java.util.EnumMap;
import com.knightshade.engine.api.StopReason;
import org.springframework.stereotype.Service;

/** Application-side bridge for optional Knightshade telemetry. */
@Service
public final class KnightshadeTelemetryService {
  private final CopyOnWriteArrayList<Consumer<SearchTelemetrySnapshot>> listeners =
      new CopyOnWriteArrayList<>();
  private volatile boolean enabled;
  private final CopyOnWriteArrayList<SearchTelemetrySnapshot> samples = new CopyOnWriteArrayList<>();
  private volatile Instant sessionStartedAt;
  private volatile int refreshFrequency = 4;
  private volatile Set<String> visibleMetrics = Set.of("depth", "mainNodes", "qNodes", "TT hit / cutoff", "beta cutoff", "PVS re-search", "null / LMR", "aspiration retries", "evaluation cache", "workers", "stopReason", "stop counters");
  private final EnumMap<StopReason, Long> stopReasonCounts = new EnumMap<>(StopReason.class);

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
    synchronized (stopReasonCounts) { stopReasonCounts.clear(); }
  }
  public java.util.Map<StopReason, Long> stopReasonCounts() { synchronized (stopReasonCounts) { return java.util.Map.copyOf(stopReasonCounts); } }

  public List<SearchTelemetrySnapshot> samples() { return List.copyOf(new ArrayList<>(samples)); }
  public Instant sessionStartedAt() { return sessionStartedAt; }
  public int refreshFrequency() { return refreshFrequency; }
  public void setRefreshFrequency(int value) { if (value < 1 || value > 10) throw new IllegalArgumentException("refresh frequency must be between 1 and 10"); refreshFrequency = value; }
  public Set<String> visibleMetrics() { return visibleMetrics; }
  public void setVisibleMetrics(Set<String> value) { visibleMetrics = Set.copyOf(value); }

  public Runnable subscribe(Consumer<SearchTelemetrySnapshot> listener) {
    Consumer<SearchTelemetrySnapshot> required = Objects.requireNonNull(listener);
    listeners.add(required);
    return () -> listeners.remove(required);
  }

  /** Delivers snapshots only while monitoring is enabled. */
  public void publish(SearchTelemetrySnapshot snapshot) {
    if (!enabled) return;
    samples.add(snapshot);
    synchronized (stopReasonCounts) { stopReasonCounts.merge(snapshot.stopReason(), 1L, Long::sum); }
    listeners.forEach(listener -> {
      try { listener.accept(snapshot); } catch (RuntimeException ignored) { }
    });
  }
}
