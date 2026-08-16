package com.escontrela.lastmove.application.player;

import com.escontrela.lastmove.domain.player.PlayerId;
import java.util.Optional;

/** Input for replacing the editable details of an existing player profile. */
public record UpdatePlayerCommand(
        PlayerId id, String email, String firstName, String lastName, Optional<byte[]> photo) {}
