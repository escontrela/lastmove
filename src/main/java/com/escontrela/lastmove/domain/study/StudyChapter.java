package com.escontrela.lastmove.domain.study;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import java.time.Instant;
import java.util.Objects;

/**
 * One ordered chapter inside a study.
 *
 * <p>A chapter is the persisted twin of an ephemeral analysis session: it owns the same chess
 * content and reading state through an {@link AnalysisDocument}, plus chapter-level metadata
 * (title, origin, timestamps). It belongs to exactly one {@link Study}.
 */
public final class StudyChapter {

  private final StudyChapterId id;
  private String title;
  private final AnalysisOrigin origin;
  private final AnalysisDocument document;
  private final Instant createdAt;
  private Instant updatedAt;

  public StudyChapter(
      StudyChapterId id,
      String title,
      AnalysisOrigin origin,
      AnalysisDocument document,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.title = requireTitle(title);
    this.origin = Objects.requireNonNull(origin, "origin must not be null");
    this.document = Objects.requireNonNull(document, "document must not be null");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }

  /** Renames this chapter while preserving its identity, content and reading state. */
  public void rename(String newTitle) {
    title = requireTitle(newTitle);
    updatedAt = Instant.now();
  }

  /** Records that the chapter content changed so its last-modified timestamp stays current. */
  public void touch() {
    updatedAt = Instant.now();
  }

  public StudyChapterId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public AnalysisOrigin origin() {
    return origin;
  }

  public AnalysisDocument document() {
    return document;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  private String requireTitle(String value) {
    String required = Objects.requireNonNull(value, "title must not be null").trim();
    if (required.isEmpty()) {
      throw new IllegalArgumentException("title must not be blank");
    }
    return required;
  }
}
