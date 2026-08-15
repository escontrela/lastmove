package com.escontrela.lastmove.infrastructure.engine.uci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UciProcessEngineTest {

  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(2);

  @Test
  void handshakesAndReturnsAnEngineNeutralMove() throws Exception {
    try (UciProcessEngine engine = engine("normal", "e7e5")) {
      engine.start().toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      var move =
          engine
              .chooseMove(request(Duration.ofMillis(50)))
              .toCompletableFuture()
              .get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      assertTrue(engine.isRunning());
      assertEquals("e7", move.from().toAlgebraic());
      assertEquals("e5", move.to().toAlgebraic());
      assertTrue(move.promotion().isEmpty());
    }
  }

  @Test
  void parsesAPromotionReturnedByTheProcess() throws Exception {
    try (UciProcessEngine engine = engine("normal", "a7a8n")) {
      var move =
          engine
              .chooseMove(request(Duration.ofMillis(50)))
              .toCompletableFuture()
              .get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      assertEquals(PieceType.KNIGHT, move.promotion().orElseThrow());
    }
  }

  @Test
  void sendsStopAndCancelsAnActiveSearch() throws Exception {
    try (UciProcessEngine engine = engine("wait-for-stop", "g8f6")) {
      var search = engine.chooseMove(request(Duration.ofSeconds(5))).toCompletableFuture();
      awaitThinking(engine);

      engine.cancelSearch();

      RuntimeException cancellation =
          assertThrows(RuntimeException.class, () -> search.join());
      Throwable cause =
          cancellation instanceof CompletionException ? cancellation.getCause() : cancellation;
      assertInstanceOf(CancellationException.class, cause);
      assertFalse(engine.isThinking());
      assertTrue(engine.isRunning());
    }
  }

  @Test
  void failsWhenTheEngineDoesNotCompleteTheHandshake() {
    try (UciProcessEngine engine = engine("no-uciok", "e7e5", Duration.ofMillis(120))) {
      CompletionException failure =
          assertThrows(
              CompletionException.class,
              () -> engine.start().toCompletableFuture().join());

      assertInstanceOf(ComputerEngineException.class, failure.getCause());
      assertFalse(engine.isRunning());
    }
  }

  @Test
  void rejectsAMalformedBestMoveWithoutLeakingProtocolDetails() {
    try (UciProcessEngine engine = engine("normal", "not-a-move")) {
      CompletionException failure =
          assertThrows(
              CompletionException.class,
              () -> engine.chooseMove(request(Duration.ofMillis(50))).toCompletableFuture().join());

      assertInstanceOf(ComputerEngineException.class, failure.getCause());
      assertFalse(engine.isThinking());
      assertFalse(engine.isRunning());
    }
  }

  @Test
  void reportsWhenTheEngineProcessDiesDuringSearch() throws Exception {
    try (UciProcessEngine engine = engine("exit-on-go", "e7e5")) {
      engine.start().toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      CompletionException failure =
          assertThrows(
              CompletionException.class,
              () -> engine.chooseMove(request(Duration.ofMillis(20))).toCompletableFuture().join());

      assertInstanceOf(ComputerEngineException.class, failure.getCause());
      assertTrue(failure.getCause().getMessage().contains("stopped unexpectedly"));
      assertFalse(engine.isRunning());
    }
  }

  @Test
  void terminatesABlockedEngineAfterTheBoundedSearchTimeout() throws Exception {
    try (UciProcessEngine engine =
        engine(
            "ignore-search",
            "e7e5",
            Duration.ofSeconds(1),
            Duration.ofMillis(60),
            Duration.ofMillis(100))) {
      engine.start().toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      CompletionException failure =
          assertThrows(
              CompletionException.class,
              () -> engine.chooseMove(request(Duration.ofMillis(20))).toCompletableFuture().join());

      assertInstanceOf(ComputerEngineException.class, failure.getCause());
      assertTrue(failure.getCause().getMessage().contains("timeout"));
      assertFalse(engine.isThinking());
      assertFalse(engine.isRunning());
    }
  }

  @Test
  void closeTerminatesTheProcessAndPreventsRestart() throws Exception {
    UciProcessEngine engine = engine("normal", "e7e5");
    engine.start().toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

    engine.close();

    assertFalse(engine.isRunning());
    CompletionException failure =
        assertThrows(CompletionException.class, () -> engine.start().toCompletableFuture().join());
    assertInstanceOf(ComputerEngineException.class, failure.getCause());
  }

  @Test
  void closeForciblyTerminatesAnEngineThatIgnoresQuit() throws Exception {
    UciProcessEngine engine =
        engine(
            "ignore-quit",
            "e7e5",
            Duration.ofSeconds(1),
            Duration.ofMillis(100),
            Duration.ofMillis(60));
    engine.start().toCompletableFuture().get(TEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

    engine.close();

    assertFalse(engine.isRunning());
  }

  private static ComputerMoveRequest request(Duration thinkingTime) {
    return new ComputerMoveRequest(
        new ChesspressoRulesEngine().startingPosition(), thinkingTime);
  }

  private static UciProcessEngine engine(String mode, String move) {
    return engine(mode, move, Duration.ofSeconds(1));
  }

  private static UciProcessEngine engine(
      String mode, String move, Duration startupTimeout) {
    return engine(
        mode,
        move,
        startupTimeout,
        Duration.ofMillis(250),
        Duration.ofMillis(250));
  }

  private static UciProcessEngine engine(
      String mode,
      String move,
      Duration startupTimeout,
      Duration searchResponseTimeout,
      Duration shutdownTimeout) {
    UciEngineConfiguration configuration =
        new UciEngineConfiguration(
            new ComputerEngineDescriptor("fake-uci", "Fake UCI", "test"),
            fakeEngineCommand(mode, move),
            Optional.empty(),
            Map.of(),
            startupTimeout,
            searchResponseTimeout,
            shutdownTimeout);
    return new UciProcessEngine(configuration, new FenService());
  }

  private static List<String> fakeEngineCommand(String mode, String move) {
    return List.of(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp",
        fakeEngineClasspath(),
        FakeUciEngineMain.class.getName(),
        mode,
        move);
  }

  private static String fakeEngineClasspath() {
    try {
      return Path.of(
              FakeUciEngineMain.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI())
          .toString();
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Could not resolve the test engine classpath", exception);
    }
  }

  private static void awaitThinking(UciProcessEngine engine) throws InterruptedException {
    long deadline = System.nanoTime() + TEST_TIMEOUT.toNanos();
    while (!engine.isThinking() && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertTrue(engine.isThinking(), "the fake engine did not begin searching");
  }
}
