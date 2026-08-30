package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.ArenaTournamentStatus;
import com.escontrela.lastmove.application.arena.LichessTournamentRequestException;
import com.escontrela.lastmove.application.arena.LichessTournamentSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps only the documented tournament schedule JSON into application-owned snapshots. */
final class LichessTournamentJsonMapper {
  private LichessTournamentJsonMapper() {}

  static List<LichessTournamentSnapshot> mapCurrentTournaments(JsonNode response) {
    if (response == null || !response.isObject()) throw invalid("Lichess returned an invalid tournament schedule.");
    List<LichessTournamentSnapshot> tournaments = new ArrayList<>();
    append(tournaments, response.path("created"), ArenaTournamentStatus.CREATED);
    append(tournaments, response.path("started"), ArenaTournamentStatus.STARTED);
    append(tournaments, response.path("finished"), ArenaTournamentStatus.FINISHED);
    return List.copyOf(tournaments);
  }

  private static void append(List<LichessTournamentSnapshot> target, JsonNode entries, ArenaTournamentStatus fallback) {
    if (entries.isMissingNode() || entries.isNull()) return;
    if (!entries.isArray()) throw invalid("Lichess returned an invalid tournament list.");
    for (JsonNode item : entries) target.add(map(item, fallback));
  }

  private static LichessTournamentSnapshot map(JsonNode node, ArenaTournamentStatus fallback) {
    String id = required(node, "id", "Tournament id");
    String name = text(node, "fullName").or(() -> text(node, "name")).orElseThrow(() -> invalid("Tournament " + id + " has no name."));
    String variant = text(node.path("variant"), "key").orElse("standard");
    JsonNode clock = node.path("clock");
    int limit = nonNegative(clock, "limit", "clock limit", id);
    int increment = nonNegative(clock, "increment", "clock increment", id);
    int minutes = nonNegative(node, "minutes", "duration", id);
    int players = nonNegative(node, "nbPlayers", "player count", id);
    return new LichessTournamentSnapshot(id, name, status(node, fallback), variant, node.path("rated").asBoolean(),
        limit, increment, minutes, players, rating(node, "minRating"), rating(node, "maxRating"),
        node.path("botsAllowed").asBoolean(false), instant(node, "startsAt"), instant(node, "finishesAt"),
        integer(node, "secondsToStart"), Optional.of("https://lichess.org/tournament/" + id));
  }

  private static ArenaTournamentStatus status(JsonNode node, ArenaTournamentStatus fallback) {
    return switch (node.path("status").asText().toLowerCase(java.util.Locale.ROOT)) {
      case "created" -> ArenaTournamentStatus.CREATED;
      case "started" -> ArenaTournamentStatus.STARTED;
      case "finished" -> ArenaTournamentStatus.FINISHED;
      case "" -> fallback;
      default -> ArenaTournamentStatus.UNKNOWN;
    };
  }

  private static Optional<Integer> rating(JsonNode node, String key) {
    JsonNode value = node.path(key);
    if (value.isMissingNode() || value.isNull()) return Optional.empty();
    if (value.isObject()) return integer(value, "rating");
    return value.canConvertToInt() ? Optional.of(value.asInt()) : Optional.empty();
  }

  private static Optional<Instant> instant(JsonNode node, String key) {
    if (!node.hasNonNull(key) || !node.path(key).canConvertToLong()) return Optional.empty();
    return Optional.of(Instant.ofEpochMilli(node.path(key).asLong()));
  }

  private static Optional<Integer> integer(JsonNode node, String key) {
    return node.hasNonNull(key) && node.path(key).canConvertToInt() ? Optional.of(node.path(key).asInt()) : Optional.empty();
  }

  private static int nonNegative(JsonNode node, String key, String label, String tournamentId) {
    Optional<Integer> value = integer(node, key);
    if (value.isEmpty() || value.get() < 0) throw invalid("Tournament " + tournamentId + " has an invalid " + label + ".");
    return value.get();
  }

  private static String required(JsonNode node, String key, String label) {
    return text(node, key).orElseThrow(() -> invalid(label + " is missing from the Lichess response."));
  }

  private static Optional<String> text(JsonNode node, String key) {
    String value = node.path(key).asText("").trim();
    return value.isEmpty() ? Optional.empty() : Optional.of(value);
  }

  private static LichessTournamentRequestException invalid(String message) {
    return new LichessTournamentRequestException(LichessTournamentRequestException.Kind.INVALID_RESPONSE, message);
  }
}
