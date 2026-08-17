package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerRepository;
import com.escontrela.lastmove.domain.user.User;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Exposes and persists the current application user.
 *
 * <p>The selection is stored with {@link Preferences} so it survives restarts. When no player has
 * been selected, or persistence is unavailable, the user defaults to {@link User#UNKNOWN}.
 */
@Service
public class CurrentUserService {

    private static final String PREFERENCE_NODE = "com.escontrela.lastmove.user";
    private static final String CURRENT_PLAYER_ID_KEY = "currentPlayerId";

    private final PlayerRepository playerRepository;
    private final PersistenceAvailability availability;
    private final Preferences preferences;

    @Autowired
    public CurrentUserService(PlayerRepository playerRepository, PersistenceAvailability availability) {
        this(playerRepository, availability, Preferences.userRoot().node(PREFERENCE_NODE));
    }

    CurrentUserService(
            PlayerRepository playerRepository,
            PersistenceAvailability availability,
            Preferences preferences) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.availability = Objects.requireNonNull(availability, "availability must not be null");
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
    }

    /** Returns the currently selected user, or {@link User#UNKNOWN} if none is selected. */
    public User currentUser() {
        return selectedPlayerId()
                .flatMap(playerRepository::findById)
                .map(player -> User.named(player.fullName()))
                .orElse(User.UNKNOWN);
    }

    /** Selects the given player profile as the current application user. */
    public void selectPlayer(PlayerId id) {
        Objects.requireNonNull(id, "id must not be null");
        preferences.put(CURRENT_PLAYER_ID_KEY, String.valueOf(id.value()));
    }

    /** Clears any selected player, returning the application user to {@link User#UNKNOWN}. */
    public void clearSelection() {
        preferences.remove(CURRENT_PLAYER_ID_KEY);
    }

    /** Returns the id of the selected player profile, if any. */
    public Optional<PlayerId> selectedPlayerId() {
        if (!availability.isAvailable()) {
            return Optional.empty();
        }
        String value = preferences.get(CURRENT_PLAYER_ID_KEY, "").trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlayerId.of(Long.parseLong(value)));
        } catch (NumberFormatException exception) {
            preferences.remove(CURRENT_PLAYER_ID_KEY);
            return Optional.empty();
        }
    }
}
