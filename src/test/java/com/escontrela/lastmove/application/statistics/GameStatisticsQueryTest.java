package com.escontrela.lastmove.application.statistics;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GameStatisticsQueryTest {
  @Test void rejectsAnInvertedDateRange() {
    assertThrows(IllegalArgumentException.class, () -> new GameStatisticsQuery(PlayerId.of(1L), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1), StatisticsGranularity.DAY, Optional.empty()));
  }
}
