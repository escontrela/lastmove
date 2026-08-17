package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.player.CreatePlayerCommand;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.player.UpdatePlayerCommand;
import com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException;
import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerRepository;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PlayerServiceTest {

    private final FakePlayerRepository repository = new FakePlayerRepository();
    private final PlayerService service = new PlayerService(repository, PersistenceAvailability.available());

    @Test
    void createsPlayer() {
        Player created =
                service.createPlayer(
                        new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));

        assertNotNull(created.id());
        assertEquals("alice@example.com", created.email());
        assertEquals("Alice", created.firstName());
        assertEquals("Smith", created.lastName());
    }

    @Test
    void rejectsDuplicateEmail() {
        service.createPlayer(
                new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));

        assertThrows(
                DuplicatePlayerEmailException.class,
                () ->
                        service.createPlayer(
                                new CreatePlayerCommand(
                                        "alice@example.com", "Alice", "Other", Optional.empty())));
    }

    @Test
    void updatesPlayerAndPreservesItsIdentity() {
        Player created =
                service.createPlayer(
                        new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));

        Player updated =
                service.updatePlayer(
                        new UpdatePlayerCommand(
                                created.id(), "alice.cooper@example.com", "Alice", "Cooper", Optional.empty()));

        assertEquals(created.id(), updated.id());
        assertEquals("Alice Cooper", service.listPlayers().getFirst().fullName());
        assertEquals("alice.cooper@example.com", service.listPlayers().getFirst().email());
    }

    @Test
    void rejectsUpdatingPlayerToAnotherPlayersEmail() {
        Player alice =
                service.createPlayer(
                        new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));
        service.createPlayer(
                new CreatePlayerCommand("bob@example.com", "Bob", "Jones", Optional.empty()));

        assertThrows(
                DuplicatePlayerEmailException.class,
                () ->
                        service.updatePlayer(
                                new UpdatePlayerCommand(
                                        alice.id(), "bob@example.com", "Alice", "Smith", Optional.empty())));
    }

    @Test
    void deletesPlayer() {
        Player created =
                service.createPlayer(
                        new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));

        service.deletePlayer(created.id());

        assertTrue(service.listPlayers().isEmpty());
    }

    @Test
    void listsPlayersWithoutPhotoBytes() {
        service.createPlayer(
                new CreatePlayerCommand("bob@example.com", "Bob", "Jones", Optional.empty()));
        service.createPlayer(
                new CreatePlayerCommand("alice@example.com", "Alice", "Smith", Optional.empty()));

        List<PlayerSummary> players = service.listPlayers();

        assertEquals(2, players.size());
        assertTrue(players.stream().allMatch(summary -> summary.email() != null));
    }

    @Test
    void reportsPersistenceAvailability() {
        assertTrue(service.isPersistenceAvailable());
        assertTrue(service.persistenceUnavailableReason().isEmpty());
    }

    @Test
    void throwsWhenPersistenceIsUnavailable() {
        PlayerService unavailableService =
                new PlayerService(repository, PersistenceAvailability.unavailable("disk full"));

        assertFalse(unavailableService.isPersistenceAvailable());
        assertEquals("disk full", unavailableService.persistenceUnavailableReason().orElseThrow());
        assertThrows(
                PersistenceUnavailableException.class,
                () ->
                        unavailableService.createPlayer(
                                new CreatePlayerCommand(
                                        "alice@example.com", "Alice", "Smith", Optional.empty())));
        assertThrows(PersistenceUnavailableException.class, unavailableService::listPlayers);
    }

    private static final class FakePlayerRepository implements PlayerRepository {

        private final AtomicLong idGenerator = new AtomicLong();
        private final Map<PlayerId, Player> playersById = new HashMap<>();

        @Override
        public Player save(Player player) {
            if (existsByEmail(player.email())) {
                throw new DuplicatePlayerEmailException(player.email());
            }
            PlayerId id = PlayerId.of(idGenerator.incrementAndGet());
            Player saved =
                    new Player(
                            id,
                            player.email(),
                            player.firstName(),
                            player.lastName(),
                            player.photo(),
                            player.createdAt());
            playersById.put(id, saved);
            return saved;
        }

        @Override
        public Player update(Player player) {
            if (existsByEmail(player.email())
                    && !findByEmail(player.email()).orElseThrow().id().equals(player.id())) {
                throw new DuplicatePlayerEmailException(player.email());
            }
            if (!playersById.containsKey(player.id())) {
                throw new IllegalArgumentException("Player no longer exists");
            }
            playersById.put(player.id(), player);
            return player;
        }

        @Override
        public void deleteById(PlayerId id) {
            playersById.remove(id);
        }

        @Override
        public Optional<Player> findById(PlayerId id) {
            return Optional.ofNullable(playersById.get(id));
        }

        @Override
        public Optional<Player> findByEmail(String email) {
            return playersById.values().stream()
                    .filter(player -> player.email().equals(email))
                    .findFirst();
        }

        @Override
        public List<Player> findAll() {
            return playersById.values().stream()
                    .sorted(Comparator.comparing(Player::firstName).thenComparing(Player::lastName))
                    .toList();
        }

        @Override
        public boolean existsByEmail(String email) {
            return playersById.values().stream().anyMatch(player -> player.email().equals(email));
        }
    }
}
