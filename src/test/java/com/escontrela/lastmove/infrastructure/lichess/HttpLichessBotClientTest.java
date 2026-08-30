package com.escontrela.lastmove.infrastructure.lichess;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpLichessBotClientTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test void selectsAnAvailableLichessPerformanceWhenStandardIsNotPresent() throws Exception {
    var perfs = json.readTree("{\"bullet\":{\"rating\":2100},\"blitz\":{\"rating\":2204}}");

    assertEquals(java.util.Optional.of(2204), HttpLichessBotAccountVerifier.ratingFrom(perfs));
  }

  @Test void deliversFragmentedNdjsonLinesIncludingDuplicatesAndOutOfOrderEvents() throws Exception {
    String ndjson = "{\"type\":\"gameState\",\"status\":\"started\",\"moves\":\"e2e4\"}\n"
        + "{\"type\":\"gameFull\",\"id\":\"g1\"}\n"
        + "{\"type\":\"gameState\",\"status\":\"started\",\"moves\":\"e2e4\"}\n";
    FakeHttpClient http = new FakeHttpClient().enqueue(streamResponse(new OneByteInputStream(ndjson)));
    HttpLichessBotClient client = new HttpLichessBotClient(http, json);
    List<String> types = new ArrayList<>(); CountDownLatch closed = new CountDownLatch(1);

    client.streamEvents("token", event -> types.add(event.path("type").asText()), ignored -> closed.countDown());

    assertEquals(true, closed.await(2, TimeUnit.SECONDS));
    assertEquals(List.of("gameState", "gameFull", "gameState"), types);
  }

  @Test void mapsHttp429ToRateLimitedTournamentFailure() {
    FakeHttpClient http = new FakeHttpClient().enqueue(response(429, "{}"));
    HttpLichessBotClient client = new HttpLichessBotClient(http, json);

    var failure = org.junit.jupiter.api.Assertions.assertThrows(
        com.escontrela.lastmove.application.arena.LichessTournamentRequestException.class,
        () -> client.currentTournaments("token"));
    assertEquals(com.escontrela.lastmove.application.arena.LichessTournamentRequestException.Kind.RATE_LIMITED, failure.kind());
  }

  private static HttpResponse<InputStream> streamResponse(InputStream body) { return new TestResponse<>(200, body); }
  private static HttpResponse<String> response(int status, String body) { return new TestResponse<>(status, body); }

  private static final class OneByteInputStream extends InputStream {
    private final ByteArrayInputStream delegate;
    OneByteInputStream(String value) { delegate = new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    @Override public int read() { return delegate.read(); }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException { return delegate.read(bytes, offset, Math.min(1, length)); }
  }

  private static final class TestResponse<T> implements HttpResponse<T> {
    private final int status; private final T body;
    TestResponse(int status, T body) { this.status = status; this.body = body; }
    public int statusCode() { return status; } public HttpRequest request() { return null; }
    public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
    public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a,b) -> true); }
    public T body() { return body; } public Optional<SSLSession> sslSession() { return Optional.empty(); }
    public URI uri() { return URI.create("https://lichess.org"); } public Version version() { return Version.HTTP_1_1; }
  }

  private static final class FakeHttpClient extends HttpClient {
    private final Deque<HttpResponse<?>> responses = new ArrayDeque<>();
    FakeHttpClient enqueue(HttpResponse<?> response) { responses.add(response); return this; }
    @SuppressWarnings("unchecked") public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) { return (HttpResponse<T>) responses.removeFirst(); }
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler) { return CompletableFuture.completedFuture(send(request, handler)); }
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> push) { return CompletableFuture.completedFuture(send(request, handler)); }
    public Optional<CookieHandler> cookieHandler() { return Optional.empty(); } public Optional<java.time.Duration> connectTimeout() { return Optional.empty(); }
    public Redirect followRedirects() { return Redirect.NEVER; } public Optional<ProxySelector> proxy() { return Optional.empty(); }
    public SSLContext sslContext() { try { return SSLContext.getDefault(); } catch (java.security.NoSuchAlgorithmException failure) { throw new AssertionError(failure); } } public SSLParameters sslParameters() { return new SSLParameters(); }
    public Optional<Authenticator> authenticator() { return Optional.empty(); } public Version version() { return Version.HTTP_1_1; }
    public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
  }
}
