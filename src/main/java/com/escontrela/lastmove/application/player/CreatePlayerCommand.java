package com.escontrela.lastmove.application.player;

import java.util.Optional;

/** Input for creating a new player profile. */
public record CreatePlayerCommand(
        String email, String firstName, String lastName, Optional<byte[]> photo) {}
