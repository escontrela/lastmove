package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.application.repository.GameStatisticsRepository;
import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.statistics.GameResultCounts;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import com.escontrela.lastmove.domain.statistics.GameStatisticsBucket;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameStatisticsServiceTest {
  @Test void delegatesTheValidatedFilterToTheReadOnlyAggregateRepository() {
    GameStatistics expected = new GameStatistics(List.of(new GameStatisticsBucket(LocalDate.of(2026, 1, 1), 3)), new GameResultCounts(2, 1, 0));
    GameStatisticsRepository repository = query -> expected;
    GameStatistics actual = new GameStatisticsService(repository).get(new GameStatisticsQuery(PlayerId.of(7L), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), StatisticsGranularity.MONTH, Optional.of("sunfish")));
    assertEquals(expected, actual);
  }
}
