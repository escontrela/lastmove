package com.escontrela.lastmove.application.statistics;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validated filters used to derive game statistics directly from saved games. */
public record GameStatisticsQuery(PlayerId playerId, LocalDate from, LocalDate to,
                                  StatisticsGranularity granularity, Optional<String> engineId) {
  public GameStatisticsQuery {
    playerId = Objects.requireNonNull(playerId, "playerId must not be null");
    from = Objects.requireNonNull(from, "from must not be null");
    to = Objects.requireNonNull(to, "to must not be null");
    granularity = Objects.requireNonNull(granularity, "granularity must not be null");
    engineId = Objects.requireNonNull(engineId, "engineId must not be null").map(String::trim).filter(value -> !value.isEmpty());
    if (from.isAfter(to)) throw new IllegalArgumentException("From date must not be after To date");
  }
}
