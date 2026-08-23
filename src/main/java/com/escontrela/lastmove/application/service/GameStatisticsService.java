package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.repository.GameStatisticsRepository;
import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Application use case for a player's aggregated computer-game history. */
@Service
public final class GameStatisticsService {
  private final GameStatisticsRepository repository;
  public GameStatisticsService(GameStatisticsRepository repository) { this.repository = Objects.requireNonNull(repository); }
  public GameStatistics get(GameStatisticsQuery query) { return repository.statisticsFor(Objects.requireNonNull(query)); }
}
