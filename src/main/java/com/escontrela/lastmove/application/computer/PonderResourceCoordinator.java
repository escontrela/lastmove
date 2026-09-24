package com.escontrela.lastmove.application.computer;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.Objects;

/** Process-wide, non-blocking admission for bounded speculation versus real engine work. */
public final class PonderResourceCoordinator {
  private static final Object MONITOR = new Object();
  private static int activeRealSearches;
  private static int activeAnalyses;
  private static PonderLease activePonder;

  private PonderResourceCoordinator() {}

  /** Strength-bar analysis yields to game searches and admitted speculation. */
  public static RealSearchLease tryBeginAnalysis() {
    synchronized (MONITOR) {
      if (activeRealSearches != 0 || activePonder != null) return null;
      activeAnalyses++;
      return new RealSearchLease(true);
    }
  }

  /** Tracks analysis already admitted by its application workflow, including guided game moves. */
  public static RealSearchLease beginAnalysis() {
    synchronized (MONITOR) {
      activeAnalyses++;
      return new RealSearchLease(true);
    }
  }

  /** Registers demand for a real search and promptly asks any speculative search to yield. */
  public static RealSearchLease beginRealSearch() {
    return beginRealSearch(null, -1);
  }

  /** Keeps only a matching game/generation context eligible for transfer into this real search. */
  public static RealSearchLease beginRealSearch(GameId gameId, long generation) {
    PonderLease ponder;
    synchronized (MONITOR) {
      activeRealSearches++;
      ponder = activePonder;
    }
    if (ponder != null && !ponder.matches(gameId, generation)
        && !ponder.canShareRealSearch(gameId)) ponder.requestCancellation();
    return new RealSearchLease();
  }

  /** Attempts to reserve the single speculative worker; never waits for capacity. */
  public static PonderLease tryStartPonder(Runnable cancellation) {
    return tryStartPonder(null, -1, cancellation);
  }

  public static PonderLease tryStartPonder(GameId gameId, long generation, Runnable cancellation) {
    return tryStartPonder(gameId, generation, false, cancellation);
  }

  /** Allows one CvC opponent search to coexist only when admission reserved CPU headroom. */
  public static PonderLease tryStartPonder(GameId gameId, long generation,
      boolean shareWithSameGameRealSearch, Runnable cancellation) {
    Objects.requireNonNull(cancellation, "cancellation must not be null");
    synchronized (MONITOR) {
      if (activeRealSearches != 0 || activeAnalyses != 0 || activePonder != null) return null;
      activePonder = new PonderLease(gameId, generation, shareWithSameGameRealSearch, cancellation);
      return activePonder;
    }
  }

  public static final class RealSearchLease implements AutoCloseable {
    private boolean closed;
    private final boolean analysis;
    private RealSearchLease() { this(false); }
    private RealSearchLease(boolean analysis) { this.analysis = analysis; }
    @Override public void close() {
      synchronized (MONITOR) {
        if (closed) return;
        closed = true;
        if (analysis) activeAnalyses = Math.max(0, activeAnalyses - 1);
        else activeRealSearches = Math.max(0, activeRealSearches - 1);
      }
    }
  }

  public static final class PonderLease implements AutoCloseable {
    private final GameId gameId;
    private final long generation;
    private final boolean shareWithSameGameRealSearch;
    private final Runnable cancellation;
    private boolean closed;
    private PonderLease(GameId gameId, long generation,
        boolean shareWithSameGameRealSearch, Runnable cancellation) {
      this.gameId = gameId;
      this.generation = generation;
      this.shareWithSameGameRealSearch = shareWithSameGameRealSearch;
      this.cancellation = cancellation;
    }
    private void requestCancellation() { cancellation.run(); }
    private boolean matches(GameId requestedGameId, long requestedGeneration) {
      return gameId != null && gameId.equals(requestedGameId) && generation == requestedGeneration;
    }
    private boolean canShareRealSearch(GameId requestedGameId) {
      return shareWithSameGameRealSearch && gameId != null && gameId.equals(requestedGameId);
    }
    @Override public void close() {
      synchronized (MONITOR) {
        if (closed) return;
        closed = true;
        if (activePonder == this) activePonder = null;
      }
    }
  }
}
