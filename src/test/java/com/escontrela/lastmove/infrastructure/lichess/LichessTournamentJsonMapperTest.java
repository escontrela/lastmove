package com.escontrela.lastmove.infrastructure.lichess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.escontrela.lastmove.application.arena.ArenaTournamentStatus;
import com.escontrela.lastmove.application.arena.LichessTournamentRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LichessTournamentJsonMapperTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test void mapsEachScheduleBucketAndTournamentEligibilityData() throws Exception {
    var response = json.readTree("""
        {"created":[{"id":"created1","fullName":"Bot Blitz","minutes":60,"clock":{"limit":180,"increment":2},"rated":true,"nbPlayers":12,"variant":{"key":"standard"},"startsAt":1788081600000,"finishesAt":1788085200000,"secondsToStart":120,"botsAllowed":true,"minRating":{"rating":1400},"maxRating":{"rating":2400}}],"started":[{"id":"started1","fullName":"Bot Rapid","minutes":90,"clock":{"limit":600,"increment":0},"rated":false,"nbPlayers":4,"variant":{"key":"standard"},"botsAllowed":true}],"finished":[{"id":"ended1","fullName":"Variant","minutes":30,"clock":{"limit":60,"increment":0},"rated":true,"nbPlayers":8,"variant":{"key":"chess960"},"botsAllowed":false}]}
        """);

    var tournaments = LichessTournamentJsonMapper.mapCurrentTournaments(response);

    assertEquals(3, tournaments.size());
    assertEquals(ArenaTournamentStatus.CREATED, tournaments.getFirst().status());
    assertEquals(1400, tournaments.getFirst().minimumRating().orElseThrow());
    assertEquals(2400, tournaments.getFirst().maximumRating().orElseThrow());
    assertEquals(ArenaTournamentStatus.STARTED, tournaments.get(1).status());
    assertEquals(ArenaTournamentStatus.FINISHED, tournaments.get(2).status());
    assertEquals("https://lichess.org/tournament/created1", tournaments.getFirst().url().orElseThrow());
  }

  @Test void rejectsAResponseWithIncompleteTournamentData() throws Exception {
    var response = json.readTree("{" + "\"created\":[{\"id\":\"missing-clock\",\"fullName\":\"Broken\"}]}" );

    var exception = assertThrows(LichessTournamentRequestException.class,
        () -> LichessTournamentJsonMapper.mapCurrentTournaments(response));

    assertEquals(LichessTournamentRequestException.Kind.INVALID_RESPONSE, exception.kind());
  }
}
