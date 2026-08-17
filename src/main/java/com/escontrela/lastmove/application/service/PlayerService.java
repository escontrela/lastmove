package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.player.CreatePlayerCommand;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.player.UpdatePlayerCommand;
import com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException;
import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerRepository;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Application service for creating and listing player profiles. */
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PersistenceAvailability availability;

    public PlayerService(PlayerRepository playerRepository, PersistenceAvailability availability) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.availability = Objects.requireNonNull(availability, "availability must not be null");
    }

    /** Creates a new player profile, rejecting duplicate emails. */
    public Player createPlayer(CreatePlayerCommand command) {
        assertAvailable();
        Objects.requireNonNull(command, "command must not be null");
        Player player =
                Player.create(
                        command.email(), command.firstName(), command.lastName(), command.photo());
        if (playerRepository.existsByEmail(player.email())) {
            throw new DuplicatePlayerEmailException(player.email());
        }
        return playerRepository.save(player);
    }

    /** Lists all persisted player profiles ordered by first name. */
    public List<PlayerSummary> listPlayers() {
        assertAvailable();
        return playerRepository.findAll().stream()
                .map(
                        player ->
                                new PlayerSummary(
                                        player.id(),
                                        player.email(),
                                        player.firstName(),
                                        player.lastName(),
                                        player.photo()))
                .toList();
    }

    /** Replaces the editable details of an existing player profile. */
    public Player updatePlayer(UpdatePlayerCommand command) {
        assertAvailable();
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.id(), "player id must not be null");
        Player existing =
                playerRepository
                        .findById(command.id())
                        .orElseThrow(() -> new IllegalArgumentException("Player no longer exists"));
        Player updated =
                existing.update(
                        command.email(), command.firstName(), command.lastName(), command.photo());
        playerRepository
                .findByEmail(updated.email())
                .filter(other -> !other.id().equals(updated.id()))
                .ifPresent(other -> { throw new DuplicatePlayerEmailException(updated.email()); });
        return playerRepository.update(updated);
    }

    /** Deletes a persisted player profile. The caller owns any current-user selection cleanup. */
    public void deletePlayer(PlayerId id) {
        assertAvailable();
        Objects.requireNonNull(id, "player id must not be null");
        playerRepository.deleteById(id);
    }

    /** Returns whether the local database is healthy enough to use. */
    public boolean isPersistenceAvailable() {
        return availability.isAvailable();
    }

    /** Returns the reason for unavailability, if known. */
    public java.util.Optional<String> persistenceUnavailableReason() {
        return availability.reason();
    }

    private void assertAvailable() {
        if (!availability.isAvailable()) {
            throw new PersistenceUnavailableException(
                    "Player persistence is unavailable"
                            + availability.reason().map(reason -> ": " + reason).orElse(""));
        }
    }
}
