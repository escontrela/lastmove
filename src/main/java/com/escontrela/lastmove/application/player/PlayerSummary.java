package com.escontrela.lastmove.application.player;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.player.PlayerType;
import java.util.Optional;

/** Lightweight read-model of a persisted player profile. */
public record PlayerSummary(
        PlayerId id,
        String email,
        String firstName,
        String lastName,
        Optional<byte[]> photo,
        PlayerType type,
        Optional<String> externalProvider,
        Optional<String> externalAccountId) {

    public PlayerSummary(PlayerId id, String email, String firstName, String lastName, Optional<byte[]> photo) {
        this(id, email, firstName, lastName, photo, PlayerType.HUMAN, Optional.empty(), Optional.empty());
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public boolean systemPlayer() { return type == PlayerType.SYSTEM; }
}
