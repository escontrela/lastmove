package com.escontrela.lastmove.application.arena;

import java.time.Instant;
import java.util.Optional;

public record ArenaChallenge(String id, Optional<String> challengerId, String challengerName,
    Optional<Integer> challengerRating, String variant, boolean rated, Optional<Integer> clockLimitSeconds,
    Optional<Integer> clockIncrementSeconds, ArenaChallengeDecision decision, Optional<String> decisionReason,
    Instant receivedAt, Optional<Instant> decidedAt, Instant updatedAt) {}
