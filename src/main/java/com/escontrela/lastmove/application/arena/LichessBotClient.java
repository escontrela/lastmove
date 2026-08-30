package com.escontrela.lastmove.application.arena;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Authenticated Lichess Bot API boundary. Stream callbacks receive one JSON event at a time. */
public interface LichessBotClient {
  StreamHandle streamEvents(String token, Consumer<JsonNode> eventConsumer, Consumer<Throwable> closedConsumer);
  StreamHandle streamGame(String token, String gameId, Consumer<JsonNode> eventConsumer, Consumer<Throwable> closedConsumer);
  JsonNode currentGames(String token);
  List<LichessTournamentSnapshot> currentTournaments(String token);
  default List<LichessBotCandidate> onlineBots(String token) { return List.of(); }
  /** Current outgoing challenge ids, used to recover a cycle after a stream reconnect. */
  default Set<String> currentOutgoingChallengeIds(String token) { return Set.of(); }
  /** Lichess can return either a pending challenge or an already-started bot game. */
  default LichessChallengeSubmission challengeBot(String token, String username, BotChallengeConfiguration configuration) { throw new UnsupportedOperationException("Outgoing bot challenges are not configured."); }
  default void cancelChallenge(String token, String challengeId) { }
  void acceptChallenge(String token, String challengeId);
  void declineChallenge(String token, String challengeId, String reason);
  void sendMove(String token, String gameId, String uci);
  void resign(String token, String gameId);
  void offerDraw(String token, String gameId);
  interface StreamHandle extends AutoCloseable { @Override void close(); }
}
