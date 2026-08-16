package com.escontrela.lastmove.application.player;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Optional;

/** Lightweight read-model of a persisted player profile. */
public record PlayerSummary(
        PlayerId id,
        String email,
        String firstName,
        String lastName,
        Optional<byte[]> photo) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
