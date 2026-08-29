package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.escontrela.lastmove.application.arena.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class LichessArenaServiceTest {
  @Test void automaticallyAcceptsStandardChallengesAndPersistsDecision() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(true, 2); FakeClient client = new FakeClient();
    LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c1\",\"rated\":true,\"variant\":{\"key\":\"standard\"},\"challenger\":{\"id\":\"u1\",\"name\":\"Alice\"}}}"));
    assertEquals(ArenaChallengeDecision.ACCEPTED, repo.challenges.get("c1").decision()); assertEquals("c1", client.accepted);
  }
  @Test void rejectsUnsupportedVariantWithVisibleReason() throws Exception {
    FakeRepository repo = new FakeRepository(); FakeSettings settings = new FakeSettings(true, 2); FakeClient client = new FakeClient(); LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c2\",\"rated\":false,\"variant\":{\"key\":\"crazyhouse\"},\"challenger\":{\"name\":\"Bob\"}}}"));
    assertEquals(ArenaChallengeDecision.DECLINED, repo.challenges.get("c2").decision()); assertTrue(repo.challenges.get("c2").decisionReason().orElseThrow().contains("standard"));
  }
  @Test void declinesWhenCapacityReservationFails() throws Exception {
    FakeRepository repo = new FakeRepository(){ @Override public boolean reserveChallenge(String id,int max){return false;} }; FakeSettings settings = new FakeSettings(true, 1); FakeClient client = new FakeClient(); LichessArenaService service = new LichessArenaService(repo, settings, client, event -> {}); service.connect();
    client.account.accept(new ObjectMapper().readTree("{\"type\":\"challenge\",\"challenge\":{\"id\":\"c3\",\"rated\":true,\"variant\":{\"key\":\"standard\"},\"challenger\":{\"name\":\"Capacity\"}}}"));
    assertEquals(ArenaChallengeDecision.DECLINED, repo.challenges.get("c3").decision());
  }
  private static final class FakeSettings implements KnightshadeArenaSettingsRepository { final KnightshadeArenaSettings value; FakeSettings(boolean auto,int max){value=new KnightshadeArenaSettings(max,auto);} public KnightshadeArenaSettings loadSettings(){return value;} public void saveSettings(KnightshadeArenaSettings s){} public Optional<String> findBotToken(){return Optional.of("token");} public void saveBotToken(String t){} public void deleteBotToken(){} public Optional<LichessBotAccount> findValidatedBotAccount(){return Optional.of(new LichessBotAccount("bot","Knightshade"));} public void saveValidatedBotAccount(LichessBotAccount a){} }
  private static class FakeRepository implements LichessArenaRepository { final Map<String,ArenaChallenge> challenges=new LinkedHashMap<>(); final Map<String,ArenaGame> games=new LinkedHashMap<>(); ArenaConnection connection=new ArenaConnection(ArenaConnectionStatus.DISCONNECTED,Optional.empty(),Optional.empty(),Optional.empty(),Instant.now()); public ArenaConnection connection(){return connection;} public void saveConnection(ArenaConnection c){connection=c;} public void saveChallenge(ArenaChallenge c){challenges.put(c.id(),c);} public Optional<ArenaChallenge> findChallenge(String id){return Optional.ofNullable(challenges.get(id));} public List<ArenaChallenge> listChallenges(){return List.copyOf(challenges.values());} public boolean reserveChallenge(String id,int max){return true;} public void saveGame(ArenaGame g){games.put(g.lichessGameId(),g);} public Optional<ArenaGame> findGame(String id){return Optional.ofNullable(games.get(id));} public List<ArenaGame> listActiveGames(){return List.copyOf(games.values());} }
  private static final class FakeClient implements LichessBotClient { String accepted; Consumer<JsonNode> account; public StreamHandle streamEvents(String t,Consumer<JsonNode> c,Consumer<Throwable> x){account=c;return ()->{};} public StreamHandle streamGame(String t,String id,Consumer<JsonNode> c,Consumer<Throwable> x){return ()->{};} public void acceptChallenge(String t,String id){accepted=id;} public void declineChallenge(String t,String id,String r){} public void sendMove(String t,String g,String u){} public void resign(String t,String g){} public void offerDraw(String t,String g){} }
}
