package com.escontrela.lastmove.domain.study;

import com.escontrela.lastmove.domain.analysis.ChapterNavigation;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for studies and their chapters.
 *
 * <p>Every read and write is scoped by the owning player so callers cannot observe another
 * player's studies. {@link #save} stores the full aggregate; {@link #updateChapterNavigation}
 * persists reading state without rewriting the move tree.
 */
public interface StudyRepository {

  /** Stores a new study or the latest state of an existing one. */
  Study save(Study study);

  /** Finds one study that belongs to the supplied owner. */
  Optional<Study> findByIdAndOwner(StudyId studyId, PlayerId ownerId);

  /** Lists the owner's studies in their user-controlled display order. */
  List<Study> findAllByOwner(PlayerId ownerId);

  /** Deletes one owned study, returning whether it existed. */
  boolean deleteByIdAndOwner(StudyId studyId, PlayerId ownerId);

  /** Deletes every study owned by the supplied player, used when the profile is removed. */
  void deleteByOwner(PlayerId ownerId);

  /** Moves one owned study to a zero-based position in the owner's display order. */
  boolean moveStudyToIndex(PlayerId ownerId, StudyId studyId, int targetIndex);

  /** Persists only the reading state of one chapter. */
  void updateChapterNavigation(
      StudyChapterId chapterId, ChapterNavigation navigation, Instant updatedAt);
}
