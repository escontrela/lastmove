package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException;
import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlitePlayerRepositoryTest {

    @TempDir
    Path tempDir;

    private SqlitePlayerRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource =
                DataSourceBuilder.create()
                        .driverClassName("org.sqlite.JDBC")
                        .url("jdbc:sqlite:" + tempDir.resolve("test.db"))
                        .build();
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository =
                new SqlitePlayerRepository(
                        new JdbcTemplate(dataSource), PersistenceAvailability.available());
    }

    @Test
    void savesAndFindsPlayer() {
        Player saved =
                repository.save(
                        Player.create(
                                "alice@example.com", "Alice", "Smith", Optional.empty()));

        Optional<Player> found = repository.findById(saved.id());

        assertTrue(found.isPresent());
        assertEquals("Alice", found.orElseThrow().firstName());
        assertEquals("Smith", found.orElseThrow().lastName());
        assertEquals("alice@example.com", found.orElseThrow().email());
    }

    @Test
    void findsByEmail() {
        repository.save(Player.create("bob@example.com", "Bob", "Jones", Optional.empty()));

        Optional<Player> found = repository.findByEmail("bob@example.com");

        assertTrue(found.isPresent());
        assertEquals("Bob", found.orElseThrow().firstName());
    }

    @Test
    void rejectsDuplicateEmail() {
        repository.save(Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        assertThrows(
                DuplicatePlayerEmailException.class,
                () ->
                        repository.save(
                                Player.create(
                                        "alice@example.com", "Alice", "Other", Optional.empty())));
    }

    @Test
    void updatesExistingPlayer() {
        Player saved =
                repository.save(Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        Player updated =
                repository.update(
                        saved.update("alice.cooper@example.com", "Alice", "Cooper", Optional.empty()));

        assertEquals(saved.id(), updated.id());
        assertEquals("Alice Cooper", repository.findById(saved.id()).orElseThrow().fullName());
        assertEquals("alice.cooper@example.com", repository.findById(saved.id()).orElseThrow().email());
    }

    @Test
    void deletesPlayer() {
        Player saved =
                repository.save(Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        repository.deleteById(saved.id());

        assertTrue(repository.findById(saved.id()).isEmpty());
    }

    @Test
    void listsPlayersOrderedByFirstName() {
        Player bob = repository.save(Player.create("bob@example.com", "Bob", "Jones", Optional.empty()));
        Player alice = repository.save(Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        List<Player> players = repository.findAll();

        assertEquals(2, players.size());
        assertEquals(alice.id(), players.get(0).id());
        assertEquals(bob.id(), players.get(1).id());
    }

    @Test
    void storesAndRetrievesPhoto() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

        Player saved =
                repository.save(
                        Player.create("photo@example.com", "Photo", "User", Optional.of(png)));

        Optional<Player> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(png.length, found.orElseThrow().photo().orElseThrow().length);
    }

    @Test
    void existsByEmail() {
        repository.save(Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        assertTrue(repository.existsByEmail("alice@example.com"));
        assertFalse(repository.existsByEmail("missing@example.com"));
    }

    @Test
    void throwsWhenUnavailable() {
        DataSource dataSource =
                DataSourceBuilder.create()
                        .driverClassName("org.sqlite.JDBC")
                        .url("jdbc:sqlite:" + tempDir.resolve("unused.db"))
                        .build();
        SqlitePlayerRepository unavailableRepository =
                new SqlitePlayerRepository(
                        new JdbcTemplate(dataSource),
                        PersistenceAvailability.unavailable("test unavailable"));

        assertThrows(
                PersistenceUnavailableException.class,
                () -> unavailableRepository.findById(PlayerId.of(1L)));
    }
}
