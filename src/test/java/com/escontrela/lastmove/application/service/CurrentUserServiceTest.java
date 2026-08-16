package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerRepository;
import com.escontrela.lastmove.domain.user.User;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CurrentUserServiceTest {

    private Preferences preferences;
    private FakePlayerRepository repository;
    private CurrentUserService service;

    @BeforeEach
    void setUp() {
        preferences = Preferences.userNodeForPackage(CurrentUserServiceTest.class)
                .node("test-" + System.nanoTime());
        repository = new FakePlayerRepository();
        service = new CurrentUserService(repository, PersistenceAvailability.available(), preferences);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void defaultsToUnknownWhenNoPlayerIsSelected() {
        assertEquals(User.UNKNOWN, service.currentUser());
        assertTrue(service.selectedPlayerId().isEmpty());
    }

    @Test
    void selectsPersistedPlayer() {
        Player player = repository.save(
                Player.create("alice@example.com", "Alice", "Smith", Optional.empty()));

        service.selectPlayer(player.id());

        assertEquals(PlayerId.of(player.id().value()), service.selectedPlayerId().orElseThrow());
        assertEquals("Alice Smith", service.currentUser().name());
    }

    @Test
    void clearSelectionReturnsUnknownUser() {
        Player player = repository.save(
                Player.create("bob@example.com", "Bob", "Jones", Optional.empty()));
        service.selectPlayer(player.id());

        service.clearSelection();

        assertTrue(service.selectedPlayerId().isEmpty());
        assertEquals(User.UNKNOWN, service.currentUser());
    }

    @Test
    void returnsUnknownWhenSelectedPlayerNoLongerExists() {
        Player player = repository.save(
                Player.create("carol@example.com", "Carol", "King", Optional.empty()));
        service.selectPlayer(player.id());
        repository.deleteById(player.id());

        assertEquals(User.UNKNOWN, service.currentUser());
        assertTrue(service.selectedPlayerId().isPresent());
    }

    @Test
    void ignoresCorruptPreferenceValue() {
        preferences.put("currentPlayerId", "not-a-number");

        assertEquals(User.UNKNOWN, service.currentUser());
        assertTrue(service.selectedPlayerId().isEmpty());
        assertEquals("", preferences.get("currentPlayerId", ""));
    }

    @Test
    void returnsUnknownWhenPersistenceIsUnavailable() {
        Player player = repository.save(
                Player.create("dave@example.com", "Dave", "Lee", Optional.empty()));
        service.selectPlayer(player.id());
        CurrentUserService unavailableService =
                new CurrentUserService(repository, PersistenceAvailability.unavailable("disk full"), preferences);

        assertEquals(User.UNKNOWN, unavailableService.currentUser());
        assertTrue(unavailableService.selectedPlayerId().isEmpty());
    }

    private static final class FakePlayerRepository implements PlayerRepository {

        private final AtomicLong idGenerator = new AtomicLong();
        private final Map<PlayerId, Player> playersById = new HashMap<>();

        @Override
        public Player save(Player player) {
            PlayerId id = PlayerId.of(idGenerator.incrementAndGet());
            Player saved = new Player(
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
