package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** SQLite-backed implementation of {@link PlayerRepository}. */
@Repository
public class SqlitePlayerRepository implements PlayerRepository {

    private static final String INSERT_SQL =
            "INSERT INTO players (email, firstname, lastname, photo, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_SQL =
            "SELECT id, email, firstname, lastname, photo, created_at FROM players";
    private static final String UPDATE_SQL =
            "UPDATE players SET email = ?, firstname = ?, lastname = ?, photo = ? WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final PersistenceAvailability availability;

    public SqlitePlayerRepository(JdbcTemplate jdbcTemplate, PersistenceAvailability availability) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.availability = Objects.requireNonNull(availability, "availability must not be null");
    }

    @Override
    public Player save(Player player) {
        assertAvailable();
        Objects.requireNonNull(player, "player must not be null");
        if (player.id() != null) {
            throw new UnsupportedOperationException("Updating existing players is not supported");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(
                    connection -> {
                        PreparedStatement statement =
                                connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
                        statement.setString(1, player.email());
                        statement.setString(2, player.firstName());
                        statement.setString(3, player.lastName());
                        statement.setBytes(4, player.photo().orElse(null));
                        statement.setLong(5, player.createdAt().toEpochMilli());
                        return statement;
                    },
                    keyHolder);
        } catch (DataAccessException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException(
                        player.email());
            }
            throw exception;
        }
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("Failed to retrieve generated player id");
        }
        return new Player(
                PlayerId.of(generatedId.longValue()),
                player.email(),
                player.firstName(),
                player.lastName(),
                player.photo(),
                player.createdAt());
    }

    @Override
    public Player update(Player player) {
        assertAvailable();
        Objects.requireNonNull(player, "player must not be null");
        if (player.id() == null) {
            throw new IllegalArgumentException("Cannot update a player without an id");
        }
        try {
            int affected =
                    jdbcTemplate.update(
                            UPDATE_SQL,
                            player.email(),
                            player.firstName(),
                            player.lastName(),
                            player.photo().orElse(null),
                            player.id().value());
            if (affected == 0) {
                throw new IllegalArgumentException("Player no longer exists");
            }
            return player;
        } catch (DataAccessException exception) {
            if (isUniqueConstraintViolation(exception)) {
                throw new com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException(
                        player.email());
            }
            throw exception;
        }
    }

    @Override
    public void deleteById(PlayerId id) {
        assertAvailable();
        Objects.requireNonNull(id, "id must not be null");
        jdbcTemplate.update("DELETE FROM players WHERE id = ?", id.value());
    }

    @Override
    public Optional<Player> findById(PlayerId id) {
        assertAvailable();
        Objects.requireNonNull(id, "id must not be null");
        return jdbcTemplate
                .query(SELECT_SQL + " WHERE id = ?", mapper(), id.value())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Player> findByEmail(String email) {
        assertAvailable();
        Objects.requireNonNull(email, "email must not be null");
        return jdbcTemplate
                .query(SELECT_SQL + " WHERE email = ?", mapper(), email)
                .stream()
                .findFirst();
    }

    @Override
    public List<Player> findAll() {
        assertAvailable();
        return jdbcTemplate.query(
                SELECT_SQL + " ORDER BY LOWER(firstname), LOWER(lastname)", mapper());
    }

    @Override
    public boolean existsByEmail(String email) {
        assertAvailable();
        Objects.requireNonNull(email, "email must not be null");
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM players WHERE email = ?",
                        Integer.class,
                        email);
        return count != null && count > 0;
    }

    private RowMapper<Player> mapper() {
        return (resultSet, rowNum) ->
                new Player(
                        PlayerId.of(resultSet.getLong("id")),
                        resultSet.getString("email"),
                        resultSet.getString("firstname"),
                        resultSet.getString("lastname"),
                        Optional.ofNullable(resultSet.getBytes("photo")),
                        Instant.ofEpochMilli(resultSet.getLong("created_at")));
    }

    private static boolean isUniqueConstraintViolation(DataAccessException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return true;
        }
        if (exception instanceof UncategorizedSQLException uncategorized) {
            java.sql.SQLException sqlException = uncategorized.getSQLException();
            return sqlException != null
                    && sqlException.getErrorCode() == 19
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains("UNIQUE constraint failed");
        }
        return false;
    }

    private void assertAvailable() {
        if (!availability.isAvailable()) {
            throw new PersistenceUnavailableException(
                    "Player persistence is unavailable"
                            + availability.reason().map(reason -> ": " + reason).orElse(""));
        }
    }
}
