package com.escontrela.lastmove.application.arena;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.function.Consumer;

/** Authenticated Lichess Bot API boundary. Stream callbacks receive one JSON event at a time. */
public interface LichessBotClient {
  StreamHandle streamEvents(String token, Consumer<JsonNode> eventConsumer, Consumer<Throwable> closedConsumer);
  StreamHandle streamGame(String token, String gameId, Consumer<JsonNode> eventConsumer, Consumer<Throwable> closedConsumer);
  JsonNode currentGames(String token);
  List<LichessTournamentSnapshot> currentTournaments(String token);
  void acceptChallenge(String token, String challengeId);
  void declineChallenge(String token, String challengeId, String reason);
  void sendMove(String token, String gameId, String uci);
  void resign(String token, String gameId);
  void offerDraw(String token, String gameId);
  interface StreamHandle extends AutoCloseable { @Override void close(); }
}
