package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.application.tag.Tag;
import com.escontrela.lastmove.application.tag.TagRepository;
import com.escontrela.lastmove.application.tag.TagTarget;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** SQLite storage for labels shared by games, studies and tactic suites. */
@Repository
public class SqliteTagRepository implements TagRepository {
  private final JdbcTemplate jdbc;
  private final PersistenceAvailability availability;

  public SqliteTagRepository(JdbcTemplate jdbc, PersistenceAvailability availability) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
    this.availability = Objects.requireNonNull(availability, "availability must not be null");
  }

  @Override
  public List<Tag> listAll() {
    if (!availability.isAvailable()) return List.of();
    return jdbc.query("SELECT id, display_name FROM tags ORDER BY normalized_name", (rs, row) -> new Tag(rs.getLong("id"), rs.getString("display_name")));
  }

  @Override
  @Transactional
  public Tag findOrCreate(String name) {
    if (!availability.isAvailable()) throw new PersistenceUnavailableException("Tags are unavailable");
    String normalized = Tag.normalizedName(name);
    String display = name.trim();
    jdbc.update("INSERT INTO tags(normalized_name,display_name,created_at) VALUES(?,?,?) ON CONFLICT(normalized_name) DO NOTHING", normalized, display, Instant.now().toEpochMilli());
    return jdbc.query("SELECT id, display_name FROM tags WHERE normalized_name=?", (rs, row) -> new Tag(rs.getLong("id"), rs.getString("display_name")), normalized).stream().findFirst().orElseThrow();
  }

  @Override
  public List<Tag> findByTarget(TagTarget target) {
    if (!availability.isAvailable()) return List.of();
    return queryByTarget(required(target));
  }

  @Override
  public Map<TagTarget, List<Tag>> findByTargets(Collection<TagTarget> targets) {
    if (!availability.isAvailable()) return Map.of();
    Map<TagTarget, List<Tag>> result = new LinkedHashMap<>();
    for (TagTarget target : targets) result.put(required(target), queryByTarget(target));
    return result;
  }

  @Override
  @Transactional
  public void assign(TagTarget target, Tag tag) {
    if (!availability.isAvailable()) throw new PersistenceUnavailableException("Tags are unavailable");
    TagTarget requiredTarget = required(target);
    jdbc.update("INSERT OR IGNORE INTO tag_assignments(tag_id,target_type,target_id,assigned_at) VALUES(?,?,?,?)", Objects.requireNonNull(tag, "tag must not be null").id(), requiredTarget.type().name(), requiredTarget.id(), Instant.now().toEpochMilli());
  }

  @Override
  @Transactional
  public void remove(TagTarget target, long tagId) {
    if (!availability.isAvailable()) return;
    TagTarget requiredTarget = required(target);
    jdbc.update("DELETE FROM tag_assignments WHERE tag_id=? AND target_type=? AND target_id=?", tagId, requiredTarget.type().name(), requiredTarget.id());
  }

  private List<Tag> queryByTarget(TagTarget target) {
    return jdbc.query("SELECT t.id,t.display_name FROM tags t JOIN tag_assignments a ON a.tag_id=t.id WHERE a.target_type=? AND a.target_id=? ORDER BY t.normalized_name", (rs, row) -> new Tag(rs.getLong("id"), rs.getString("display_name")), target.type().name(), target.id());
  }

  private static TagTarget required(TagTarget target) {
    return Objects.requireNonNull(target, "target must not be null");
  }
}
