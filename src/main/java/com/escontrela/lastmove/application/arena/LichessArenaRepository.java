package com.escontrela.lastmove.application.arena;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for the observable Arena state. */
public interface LichessArenaRepository {
  ArenaConnection connection();
  void saveConnection(ArenaConnection connection);
  void saveChallenge(ArenaChallenge challenge);
  Optional<ArenaChallenge> findChallenge(String id);
  List<ArenaChallenge> listChallenges();
  /** Removes every persisted challenge row (incoming and outgoing log entries). */
  default void clearChallenges() { }
  /** Atomically reserves one accepting slot when fewer than {@code maximum} games are reserved or active. */
  boolean reserveChallenge(String id, int maximum);
  void saveGame(ArenaGame game);
  Optional<ArenaGame> findGame(String lichessGameId);
  List<ArenaGame> listActiveGames();
  void saveTournament(ArenaTournament tournament);
  Optional<ArenaTournament> findTournament(String lichessTournamentId);
  List<ArenaTournament> listTournaments();
  default BotChallengeCycle botChallengeCycle() { return BotChallengeCycle.idle(); }
  default void saveBotChallengeCycle(BotChallengeCycle cycle) { }
}
