package com.escontrela.lastmove.infrastructure.lichess;

import com.escontrela.lastmove.application.arena.LichessBotAccount;
import com.escontrela.lastmove.application.arena.LichessBotAccountValidationException;
import com.escontrela.lastmove.application.arena.LichessBotAccountVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import org.springframework.stereotype.Component;

/** Lichess HTTP adapter used only to validate the configured account before Arena connects. */
@Component
public class HttpLichessBotAccountVerifier implements LichessBotAccountVerifier {
  private static final URI ACCOUNT_URI = URI.create("https://lichess.org/api/account");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient client;
  private final ObjectMapper objectMapper;

  public HttpLichessBotAccountVerifier() {
    this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), new ObjectMapper());
  }

  HttpLichessBotAccountVerifier(HttpClient client, ObjectMapper objectMapper) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public LichessBotAccount verifyBotToken(String token) {
    String requiredToken = Objects.requireNonNull(token, "token must not be null").trim();
    if (requiredToken.isEmpty()) throw new LichessBotAccountValidationException("Lichess bot token must not be blank.");
    HttpRequest request = HttpRequest.newBuilder(ACCOUNT_URI)
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + requiredToken)
        .GET()
        .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return accountFrom(response);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LichessBotAccountValidationException("Lichess account validation was interrupted.", exception);
    } catch (IOException exception) {
      throw new LichessBotAccountValidationException("Could not reach Lichess to validate the bot account.", exception);
    }
  }

  private LichessBotAccount accountFrom(HttpResponse<String> response) {
    return switch (response.statusCode()) {
      case 200 -> parseBotAccount(response.body());
      case 401, 403 -> throw new LichessBotAccountValidationException("Lichess rejected the bot token.");
      case 429 -> throw new LichessBotAccountValidationException("Lichess is rate-limiting requests. Wait one minute before trying again.");
      default -> throw new LichessBotAccountValidationException(
          "Lichess account validation failed (HTTP " + response.statusCode() + ").");
    };
  }

  private LichessBotAccount parseBotAccount(String body) {
    try {
      JsonNode account = objectMapper.readTree(body);
      if (!"BOT".equalsIgnoreCase(account.path("title").asText())) {
        throw new LichessBotAccountValidationException("The configured Lichess account is not a bot account.");
      }
      JsonNode performances = account.path("perfs");
      return new LichessBotAccount(account.path("id").asText(), account.path("username").asText(),
          ratingFrom(performances, "blitz"), ratingFrom(performances, "rapid"),
          ratingFrom(performances, "standard"), Optional.empty());
    } catch (IOException | IllegalArgumentException exception) {
      throw new LichessBotAccountValidationException("Lichess returned an invalid account response.", exception);
    }
  }

  static Optional<Integer> ratingFrom(JsonNode performances, String performance) {
    JsonNode rating = performances.path(performance).path("rating");
    if (rating.isInt() || rating.isLong()) return Optional.of(rating.asInt());
    return Optional.empty();
  }

  static Optional<Integer> ratingFrom(JsonNode performances) {
    for (String performance : List.of("blitz", "rapid", "bullet", "classical", "correspondence")) {
      Optional<Integer> rating = ratingFrom(performances, performance);
      if (rating.isPresent()) return rating;
    }
    return Optional.empty();
  }
}
