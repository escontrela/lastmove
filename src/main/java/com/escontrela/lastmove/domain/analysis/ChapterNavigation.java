package com.escontrela.lastmove.domain.analysis;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reading state of a user inside one analysis tree.
 *
 * <p>This value object deliberately holds no chess content: it records the cursor node, the
 * preferred root variation and the preferred continuation chosen at every branch, plus the last
 * time the position was visited. Chess content (initial position, moves, variations and their
 * order) belongs to {@link AnalysisContent}.
 */
public final class ChapterNavigation {

  private final Optional<AnalysisNodeId> currentNodeId;
  private final Optional<AnalysisNodeId> selectedRootNodeId;
  private final Map<AnalysisNodeId, AnalysisNodeId> selectedContinuationIds;
  private final Optional<Instant> lastVisitedAt;

  /** Creates a fresh navigation state with no position visited yet. */
  public ChapterNavigation() {
    this(Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
  }

  public ChapterNavigation(
      Optional<AnalysisNodeId> currentNodeId,
      Optional<AnalysisNodeId> selectedRootNodeId,
      Map<AnalysisNodeId, AnalysisNodeId> selectedContinuationIds,
      Optional<Instant> lastVisitedAt) {
    this.currentNodeId = Objects.requireNonNull(currentNodeId, "currentNodeId must not be null");
    this.selectedRootNodeId =
        Objects.requireNonNull(selectedRootNodeId, "selectedRootNodeId must not be null");
    this.selectedContinuationIds =
        Map.copyOf(
            Objects.requireNonNull(
                selectedContinuationIds, "selectedContinuationIds must not be null"));
    this.lastVisitedAt = Objects.requireNonNull(lastVisitedAt, "lastVisitedAt must not be null");
  }

  public Optional<AnalysisNodeId> currentNodeId() {
    return currentNodeId;
  }

  public Optional<AnalysisNodeId> selectedRootNodeId() {
    return selectedRootNodeId;
  }

  public Map<AnalysisNodeId, AnalysisNodeId> selectedContinuationIds() {
    return selectedContinuationIds;
  }

  public Optional<Instant> lastVisitedAt() {
    return lastVisitedAt;
  }

  /** Returns a copy of this state with a different cursor node, keeping the selected line. */
  public ChapterNavigation withCurrentNodeId(Optional<AnalysisNodeId> nodeId) {
    return new ChapterNavigation(
        Objects.requireNonNull(nodeId, "nodeId must not be null"),
        selectedRootNodeId,
        selectedContinuationIds,
        lastVisitedAt);
  }

  /** Returns a copy of this state with a new selected route, keeping the cursor unchanged. */
  public ChapterNavigation withSelectedLine(
      Optional<AnalysisNodeId> rootNodeId, Map<AnalysisNodeId, AnalysisNodeId> continuations) {
    return new ChapterNavigation(
        currentNodeId,
        Objects.requireNonNull(rootNodeId, "rootNodeId must not be null"),
        Objects.requireNonNull(continuations, "continuations must not be null"),
        lastVisitedAt);
  }

  /** Returns a copy of this state with the last-visited timestamp replaced. */
  public ChapterNavigation withLastVisitedAt(Instant visitedAt) {
    return new ChapterNavigation(
        currentNodeId,
        selectedRootNodeId,
        selectedContinuationIds,
        Optional.of(Objects.requireNonNull(visitedAt, "visitedAt must not be null")));
  }

  /**
   * Returns a copy of this state whose node identities are rewritten through the supplied mapping.
   *
   * <p>Deep-copying an analysis tree produces new node identities; this method keeps the reading
   * state coherent with that copy.
   */
  public ChapterNavigation remapped(Map<AnalysisNodeId, AnalysisNodeId> remapping) {
    Objects.requireNonNull(remapping, "remapping must not be null");
    return new ChapterNavigation(
        currentNodeId.map(remapping::get),
        selectedRootNodeId.map(remapping::get),
        selectedContinuationIds.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> remapping.get(entry.getKey()),
                    entry -> remapping.get(entry.getValue()))),
        lastVisitedAt);
  }
}
