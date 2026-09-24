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
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.HashSet;
import com.knightshade.engine.api.StopReason;
import com.knightshade.engine.api.SearchTelemetryEvent;
import org.springframework.stereotype.Service;

/** Application-side bridge for optional Knightshade telemetry. */
@Service
public final class KnightshadeTelemetryService {
  private final CopyOnWriteArrayList<Consumer<SearchTelemetrySnapshot>> listeners =
      new CopyOnWriteArrayList<>();
  private volatile boolean enabled;
  private final List<SearchTelemetrySnapshot> samples = new ArrayList<>();
  private volatile Instant sessionStartedAt;
  private volatile String gameId = "unscoped";
  private volatile int refreshFrequency = 4;
  private volatile Set<String> visibleMetrics = Set.of(
      "depth", "time to depth (ms)", "post-depth time (ms)", "post-depth nodes",
      "mainNodes", "qNodes", "TT hit / cutoff", "beta cutoff", "PVS re-search",
      "null / LMR", "aspiration retries", "mate confirmations", "evaluation cache", "workers", "qsearch", "stand-pat",
      "move lists", "quiet checks", "SEE", "search", "NPS", "stopReason", "stop counters",
      "ponder starts", "ponder hit rate", "ponder reused depth");
  private final EnumMap<StopReason, Long> stopReasonCounts = new EnumMap<>(StopReason.class);
  private long ponderHits;
  private long ponderStarts;
  private final Set<String> ponderStartIds = new HashSet<>();
  private long ponderMisses;
  private int lastPonderReusedDepth;
  private final List<Integer> ponderReusedDepthSamples = new ArrayList<>();
  private final Set<String> ponderDecisionIds = new HashSet<>();

  public boolean isEnabled() { return enabled; }

  public void setEnabled(boolean enabled) {
    boolean wasEnabled = this.enabled;
    this.enabled = enabled;
    if (enabled && !wasEnabled) beginSession();
  }

  /** Starts a new game session, discarding samples from the previous game. */
  public void beginSession() {
    beginSession("unscoped");
  }

  /** Starts a game-scoped telemetry session. */
  public synchronized void beginSession(String gameId) {
    samples.clear();
    sessionStartedAt = Instant.now();
    this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
    stopReasonCounts.clear();
    ponderHits = 0;
    ponderStarts = 0;
    ponderStartIds.clear();
    ponderMisses = 0;
    lastPonderReusedDepth = 0;
    ponderReusedDepthSamples.clear();
    ponderDecisionIds.clear();
  }
  public synchronized java.util.Map<StopReason, Long> stopReasonCounts() { return java.util.Map.copyOf(stopReasonCounts); }

  public synchronized List<SearchTelemetrySnapshot> samples() { return List.copyOf(samples); }
  public Instant sessionStartedAt() { return sessionStartedAt; }
  public String gameId() { return gameId; }
  public int refreshFrequency() { return refreshFrequency; }
  public void setRefreshFrequency(int value) { if (value < 1 || value > 10) throw new IllegalArgumentException("refresh frequency must be between 1 and 10"); refreshFrequency = value; }
  public Set<String> visibleMetrics() { return visibleMetrics; }
  public void setVisibleMetrics(Set<String> value) { visibleMetrics = Set.copyOf(value); }

  public synchronized long ponderHits() { return ponderHits; }
  public synchronized long ponderStarts() { return ponderStarts; }
  public synchronized long ponderMisses() { return ponderMisses; }
  public synchronized long ponderDecisions() { return ponderHits + ponderMisses; }
  public synchronized OptionalInt lastPonderReusedDepth() {
    return lastPonderReusedDepth > 0 ? OptionalInt.of(lastPonderReusedDepth) : OptionalInt.empty();
  }
  public synchronized OptionalDouble ponderHitRate() {
    long decisions = ponderHits + ponderMisses;
    return decisions == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) ponderHits / decisions);
  }
  public synchronized OptionalDouble ponderReusedDepthAverage() {
    return ponderReusedDepthSamples.isEmpty() ? OptionalDouble.empty()
        : OptionalDouble.of(ponderReusedDepthSamples.stream().mapToInt(Integer::intValue).average().orElseThrow());
  }
  public synchronized OptionalInt ponderReusedDepthMax() {
    return ponderReusedDepthSamples.isEmpty() ? OptionalInt.empty()
        : OptionalInt.of(ponderReusedDepthSamples.stream().mapToInt(Integer::intValue).max().orElseThrow());
  }

  public Runnable subscribe(Consumer<SearchTelemetrySnapshot> listener) {
    Consumer<SearchTelemetrySnapshot> required = Objects.requireNonNull(listener);
    listeners.add(required);
    return () -> listeners.remove(required);
  }

  /** Delivers snapshots only while monitoring is enabled. */
  public void publish(SearchTelemetrySnapshot snapshot) {
    if (!enabled) return;
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    synchronized (this) {
      if ("unscoped".equals(gameId)) gameId = snapshot.context().gameId();
      if (!gameId.equals(snapshot.context().gameId())) return;
      if (snapshot.event() == SearchTelemetryEvent.PONDER_STARTED) {
        if (!ponderStartIds.add(decisionId(snapshot))) return;
        ponderStarts++;
      } else if (snapshot.event() == SearchTelemetryEvent.PONDER_HIT) {
        if (!ponderDecisionIds.add(decisionId(snapshot))) return;
        ponderHits++;
        if (snapshot.ponderReusedDepth() > 0) {
          lastPonderReusedDepth = snapshot.ponderReusedDepth();
          ponderReusedDepthSamples.add(snapshot.ponderReusedDepth());
        }
      } else if (snapshot.event() == SearchTelemetryEvent.PONDER_MISS) {
        if (!ponderDecisionIds.add(decisionId(snapshot))) return;
        ponderMisses++;
      }
      samples.add(snapshot);
      if (snapshot.event() == SearchTelemetryEvent.SEARCH_FINISHED) {
        stopReasonCounts.merge(snapshot.stopReason(), 1L, Long::sum);
      }
    }
    listeners.forEach(listener -> {
      try { listener.accept(snapshot); } catch (RuntimeException ignored) { }
    });
  }

  private static String decisionId(SearchTelemetrySnapshot snapshot) {
    return snapshot.context().gameId() + "/" + snapshot.context().searchId();
  }
}
