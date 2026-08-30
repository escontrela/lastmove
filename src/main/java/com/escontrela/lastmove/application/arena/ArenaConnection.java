package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

public record ArenaConnection(ArenaConnectionStatus status, Optional<String> lastError,
    Optional<Instant> connectedAt, Optional<Instant> disconnectedAt, Instant updatedAt) {}
