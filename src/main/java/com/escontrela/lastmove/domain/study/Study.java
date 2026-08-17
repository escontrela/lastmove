package com.escontrela.lastmove.domain.study;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root for a player's persisted study and its ordered chapters.
 *
 * <p>Owns the study metadata and the ordered chapter list. A chapter is a contained entity that
 * can belong to only one study; ordering is explicit and preserved by every chapter mutation.
 */
public final class Study {

  private final StudyId id;
  private final PlayerId ownerId;
  private String title;
  private Optional<String> description;
  private final List<StudyChapter> chapters = new ArrayList<>();
  private final Instant createdAt;
  private Instant updatedAt;

  private Study(
      StudyId id,
      PlayerId ownerId,
      String title,
      Optional<String> description,
      List<StudyChapter> chapters,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
    this.title = requireTitle(title);
    this.description = Objects.requireNonNull(description, "description must not be null");
    this.chapters.addAll(Objects.requireNonNull(chapters, "chapters must not be null"));
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  /** Creates a new, not-yet-persisted study without chapters. */
  public static Study create(PlayerId ownerId, String title) {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Instant now = Instant.now();
    return new Study(StudyId.random(), ownerId, title, Optional.empty(), List.of(), now, now);
  }

  /** Rehydrates a persisted study with its complete state. */
  public static Study restore(
      StudyId id,
      PlayerId ownerId,
      String title,
      Optional<String> description,
      List<StudyChapter> chapters,
      Instant createdAt,
      Instant updatedAt) {
    return new Study(id, ownerId, title, description, chapters, createdAt, updatedAt);
  }

  /** Renames this study while preserving its identity, owner and chapters. */
  public void rename(String newTitle) {
    title = requireTitle(newTitle);
    updatedAt = Instant.now();
  }

  public void setDescription(Optional<String> newDescription) {
    description = Objects.requireNonNull(newDescription, "description must not be null");
    updatedAt = Instant.now();
  }

  /** Appends a chapter at the end of the study's ordered chapter list. */
  public StudyChapter addChapter(StudyChapter chapter) {
    Objects.requireNonNull(chapter, "chapter must not be null");
    chapters.add(chapter);
    updatedAt = Instant.now();
    return chapter;
  }

  /** Removes a chapter and returns whether it existed. */
  public boolean removeChapter(StudyChapterId chapterId) {
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    boolean removed = chapters.removeIf(chapter -> chapter.id().equals(chapterId));
    if (removed) {
      updatedAt = Instant.now();
    }
    return removed;
  }

  /** Moves one chapter by an offset of one, returning whether the move applied. */
  public boolean moveChapter(StudyChapterId chapterId, int offset) {
    Objects.requireNonNull(chapterId, "chapterId must not be null");
    int currentIndex = indexOf(chapterId);
    if (currentIndex < 0) {
      return false;
    }
    int targetIndex = currentIndex + offset;
    if (targetIndex < 0 || targetIndex >= chapters.size()) {
      return false;
    }
    StudyChapter chapter = chapters.remove(currentIndex);
    chapters.add(targetIndex, chapter);
    updatedAt = Instant.now();
    return true;
  }

  /** Records that the study changed so its last-modified timestamp stays current. */
  public void touch() {
    updatedAt = Instant.now();
  }

  public Optional<StudyChapter> chapter(StudyChapterId chapterId) {
    return chapters.stream().filter(chapter -> chapter.id().equals(chapterId)).findFirst();
  }

  public StudyId id() {
    return id;
  }

  public PlayerId ownerId() {
    return ownerId;
  }

  public String title() {
    return title;
  }

  public Optional<String> description() {
    return description;
  }

  /** Returns the chapters in their user-controlled display order. */
  public List<StudyChapter> chapters() {
    return List.copyOf(chapters);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  private int indexOf(StudyChapterId chapterId) {
    for (int index = 0; index < chapters.size(); index++) {
      if (chapters.get(index).id().equals(chapterId)) {
        return index;
      }
    }
    return -1;
  }

  private String requireTitle(String value) {
    String required = Objects.requireNonNull(value, "title must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return required;
  }
}
