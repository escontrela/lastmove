package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.study.StudyAnnotationRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Updates annotations independently, without rewriting a study's move tree. */
@Repository
public class SqliteStudyAnnotationRepository implements StudyAnnotationRepository {
  private final JdbcTemplate jdbc;
  private final PersistenceAvailability availability;

  public SqliteStudyAnnotationRepository(JdbcTemplate jdbc, PersistenceAvailability availability) {
    this.jdbc = jdbc;
    this.availability = availability;
  }

  public Optional<String> studyComment(StudyId id) { return value("SELECT comment FROM study_comments WHERE study_id = ?", id.value().toString()); }
  public Optional<String> chapterComment(StudyChapterId id) { return value("SELECT comment FROM study_chapter_comments WHERE chapter_id = ?", id.value().toString()); }
  public Optional<String> moveComment(StudyChapterId chapterId, AnalysisNodeId nodeId) { return value("SELECT comment FROM study_move_comments WHERE chapter_id = ? AND node_id = ?", chapterId.value().toString(), nodeId.value().toString()); }

  public void saveStudyComment(StudyId id, String comment) { save("study_comments", "study_id", id.value().toString(), null, comment); }
  public void saveChapterComment(StudyChapterId id, String comment) { save("study_chapter_comments", "chapter_id", id.value().toString(), null, comment); }
  public void saveMoveComment(StudyChapterId chapterId, AnalysisNodeId nodeId, String comment) { save("study_move_comments", "chapter_id", chapterId.value().toString(), nodeId.value().toString(), comment); }

  private Optional<String> value(String sql, Object... args) {
    assertAvailable();
    return jdbc.query(sql, (rs, row) -> rs.getString(1), args).stream().findFirst();
  }

  private void save(String table, String key, String id, String nodeId, String comment) {
    assertAvailable();
    if (comment.isBlank()) {
      if (nodeId == null) jdbc.update("DELETE FROM " + table + " WHERE " + key + " = ?", id);
      else jdbc.update("DELETE FROM " + table + " WHERE chapter_id = ? AND node_id = ?", id, nodeId);
      return;
    }
    long now = Instant.now().toEpochMilli();
    if (nodeId == null) {
      jdbc.update("INSERT INTO " + table + " (" + key + ", comment, updated_at) VALUES (?, ?, ?) ON CONFLICT(" + key + ") DO UPDATE SET comment=excluded.comment, updated_at=excluded.updated_at", id, comment, now);
    } else {
      jdbc.update("INSERT INTO study_move_comments (chapter_id, node_id, comment, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT(chapter_id, node_id) DO UPDATE SET comment=excluded.comment, updated_at=excluded.updated_at", id, nodeId, comment, now);
    }
  }

  private void assertAvailable() {
    if (!availability.isAvailable()) throw new PersistenceUnavailableException(availability.reason().orElse("Persistence unavailable"));
  }
}
