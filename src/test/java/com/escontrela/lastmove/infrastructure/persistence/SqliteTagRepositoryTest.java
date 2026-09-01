package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.tag.Tag;
import com.escontrela.lastmove.application.tag.TagTarget;
import com.escontrela.lastmove.application.tag.TagTargetType;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteTagRepositoryTest {
  @TempDir Path tempDir;
  private SqliteTagRepository repository;

  @BeforeEach void setUp() {
    DataSource source = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC").url("jdbc:sqlite:" + tempDir.resolve("tags.db")).build();
    Flyway.configure().dataSource(source).load().migrate();
    repository = new SqliteTagRepository(new JdbcTemplate(source), PersistenceAvailability.available());
  }

  @Test void createsTagsOnceRegardlessOfCasingAndAssignsThemToIndependentResourceTypes() {
    Tag attacking = repository.findOrCreate("Attacking");
    Tag existing = repository.findOrCreate("attacking");
    Tag endgame = repository.findOrCreate("Endgame");
    TagTarget game = new TagTarget(TagTargetType.GAME, "game-1");
    TagTarget study = new TagTarget(TagTargetType.STUDY, "study-1");

    repository.assign(game, attacking);
    repository.assign(game, attacking);
    repository.assign(game, endgame);
    repository.assign(study, attacking);

    assertEquals(attacking.id(), existing.id());
    assertEquals(List.of("Attacking", "Endgame"), repository.findByTarget(game).stream().map(Tag::name).toList());
    assertEquals(List.of("Attacking"), repository.findByTarget(study).stream().map(Tag::name).toList());
    assertEquals(List.of("Attacking", "Endgame"), repository.listAll().stream().map(Tag::name).toList());
  }

  @Test void removesOnlyTheRequestedAssignment() {
    Tag tag = repository.findOrCreate("Blitz");
    TagTarget first = new TagTarget(TagTargetType.GAME, "game-1");
    TagTarget second = new TagTarget(TagTargetType.GAME, "game-2");
    repository.assign(first, tag);
    repository.assign(second, tag);

    repository.remove(first, tag.id());

    assertTrue(repository.findByTarget(first).isEmpty());
    assertEquals(List.of("Blitz"), repository.findByTarget(second).stream().map(Tag::name).toList());
  }
}
