package com.escontrela.lastmove.application.repository;

import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.statistics.GameStatistics;

/** Read-only boundary for aggregates calculated from persisted chess games. */
public interface GameStatisticsRepository {
  GameStatistics statisticsFor(GameStatisticsQuery query);
}
