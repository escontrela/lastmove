package com.escontrela.lastmove.application.arena;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameId;
import java.time.Instant;
import java.util.Optional;

public record ArenaGame(String lichessGameId, Optional<GameId> localGameId, Optional<String> challengeId,
    Optional<String> gameUrl, Optional<String> whiteLichessId, Optional<String> blackLichessId,
    Optional<PieceColor> botColor, ArenaGameStatus status, Optional<String> lastError,
    Instant startedAt, Optional<Instant> finishedAt, Instant updatedAt) {}
