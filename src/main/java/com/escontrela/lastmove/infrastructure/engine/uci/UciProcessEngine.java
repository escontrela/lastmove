package com.escontrela.lastmove.infrastructure.engine.uci;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineException;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.EngineAnalysisResult;
import com.escontrela.lastmove.application.computer.EngineScore;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.service.FenService;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Generic asynchronous {@link ComputerMoveEngine} backed by an external UCI process.
 *
 * <p>All protocol conversations are serialized on one virtual thread. Standard output is consumed
 * independently into a queue, allowing every handshake and search to have a bounded timeout. The
 * engine process may be Python, Java or native as long as it implements UCI over standard streams.
 */
public final class UciProcessEngine implements ComputerMoveEngine {

  private static final String END_OF_OUTPUT = "\u0000<UCI-EOF>";

  private final UciEngineConfiguration configuration;
  private final FenService fenService;
  private final ExecutorService protocolExecutor =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("uci-protocol-", 0).factory());
  private final BlockingQueue<String> outputLines = new LinkedBlockingQueue<>();
  private final Object lifecycleMonitor = new Object();
  private final Object writerMonitor = new Object();
  private final AtomicBoolean cancellationRequested = new AtomicBoolean();

  private volatile Process process;
  private volatile BufferedWriter processInput;
  private volatile Thread outputReader;
  private volatile boolean ready;
  private volatile boolean thinking;
  private volatile boolean searchCommandSent;
  private volatile boolean closed;

  public UciProcessEngine(UciEngineConfiguration configuration, FenService fenService) {
    this.configuration =
        Objects.requireNonNull(configuration, "configuration must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  @Override
  public ComputerEngineDescriptor descriptor() {
    return configuration.descriptor();
  }

  @Override
  public CompletionStage<Void> start() {
    if (closed) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    try {
      return CompletableFuture.runAsync(this::ensureStarted, protocolExecutor);
    } catch (RejectedExecutionException exception) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public boolean isRunning() {
    Process current = process;
    return ready && !closed && current != null && current.isAlive();
  }

  @Override
  public boolean isThinking() {
    return thinking;
  }

  @Override
  public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
    ComputerMoveRequest required =
        Objects.requireNonNull(request, "request must not be null");
    if (closed) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    try {
      return CompletableFuture.supplyAsync(
          () -> chooseMoveBlocking(required), protocolExecutor);
    } catch (RejectedExecutionException exception) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public CompletionStage<EngineAnalysisResult> analyze(ComputerMoveRequest request) {
    ComputerMoveRequest required =
        Objects.requireNonNull(request, "request must not be null");
    if (closed) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
    try {
      return CompletableFuture.supplyAsync(
          () -> analyzeBlocking(required), protocolExecutor);
    } catch (RejectedExecutionException exception) {
      return CompletableFuture.failedFuture(closedEngineException());
    }
  }

  @Override
  public void cancelSearch() {
    if (!thinking) {
      return;
    }
    cancellationRequested.set(true);
    if (searchCommandSent) {
      trySend("stop");
    }
  }

  @Override
  public void close() {
    synchronized (lifecycleMonitor) {
      if (closed) {
        return;
      }
      closed = true;
      cancellationRequested.set(true);
      Process current = process;
      if (current != null && current.isAlive()) {
        trySend("stop");
        trySend("quit");
        awaitExit(current, configuration.shutdownTimeout());
      }
      ready = false;
      thinking = false;
      searchCommandSent = false;
      Thread reader = outputReader;
      if (reader != null) {
        reader.interrupt();
      }
      protocolExecutor.shutdownNow();
    }
  }

  private MoveCommand chooseMoveBlocking(ComputerMoveRequest request) {
    EngineAnalysisResult result = analyzeBlocking(request);
    return result
        .bestMove()
        .orElseThrow(
            () ->
                new ComputerEngineException(
                    "The UCI engine " + descriptor().displayName() + " returned no playable move"));
  }

  private EngineAnalysisResult analyzeBlocking(ComputerMoveRequest request) {
    ensureStarted();
    thinking = true;
    searchCommandSent = false;
    cancellationRequested.set(false);
    try {
      String fen = fenService.fromSnapshot(request.position()).getValue();
      sendCommand("position fen " + fen);
      sendCommand("go movetime " + positiveMillis(request.maximumThinkingTime()));
      searchCommandSent = true;
      if (cancellationRequested.get()) {
        sendCommand("stop");
      }
      Duration responseTimeout =
          request.maximumThinkingTime().plus(configuration.searchResponseTimeout());
      return awaitBestMoveWithInfo(responseTimeout);
    } catch (ComputerEngineException exception) {
      synchronized (lifecycleMonitor) {
        terminateProcess();
      }
      throw exception;
    } finally {
      thinking = false;
      searchCommandSent = false;
      cancellationRequested.set(false);
    }
  }

  private EngineAnalysisResult awaitBestMoveWithInfo(Duration timeout) {
    long remainingNanos = timeout.toNanos();
    long deadline = System.nanoTime() + remainingNanos;
    Optional<EngineScore> score = Optional.empty();
    Optional<Integer> depth = Optional.empty();
    try {
      while (remainingNanos > 0) {
        String line = outputLines.poll(remainingNanos, TimeUnit.NANOSECONDS);
        if (line == null) {
          break;
        }
        if (END_OF_OUTPUT.equals(line)) {
          throw new ComputerEngineException(
              "UCI engine " + descriptor().displayName() + " stopped unexpectedly");
        }
        if (line.startsWith("bestmove ")) {
          if (cancellationRequested.get()) {
            throw new CancellationException("The UCI search was cancelled");
          }
          return new EngineAnalysisResult(parseBestMoveToken(line), score, depth, Optional.empty());
        }
        if (line.startsWith("info ")) {
          Optional<EngineScore> parsedScore = UciInfoParser.parseScore(line);
          if (parsedScore.isPresent()) {
            score = parsedScore;
          }
          Optional<Integer> parsedDepth = UciInfoParser.parseDepth(line);
          if (parsedDepth.isPresent()) {
            depth = parsedDepth;
          }
        }
        remainingNanos = deadline - System.nanoTime();
      }
      throw new ComputerEngineException(
          "UCI engine " + descriptor().displayName() + " did not respond before the timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ComputerEngineException("Interrupted while waiting for the UCI engine", exception);
    }
  }

  private void ensureStarted() {
    synchronized (lifecycleMonitor) {
      if (isRunning()) {
        return;
      }
      if (closed) {
        throw new ComputerEngineException("The UCI engine has already been closed");
      }
      terminateProcess();
      try {
        ProcessBuilder builder = new ProcessBuilder(configuration.command());
        configuration.workingDirectory().ifPresent(path -> builder.directory(path.toFile()));
        builder.environment().putAll(configuration.environment());
        builder.redirectErrorStream(true);
        process = builder.start();
        processInput =
            new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        outputLines.clear();
        startOutputReader(process);
        sendCommand("uci");
        awaitLine("uciok"::equals, configuration.startupTimeout());
        sendCommand("isready");
        awaitLine("readyok"::equals, configuration.startupTimeout());
        ready = true;
      } catch (IOException exception) {
        terminateProcess();
        throw new ComputerEngineException(
            "Could not start UCI engine " + descriptor().displayName(), exception);
      } catch (RuntimeException exception) {
        terminateProcess();
        throw exception;
      }
    }
  }

  private void startOutputReader(Process startedProcess) {
    outputReader =
        Thread.ofVirtual()
            .name("uci-output-" + descriptor().id())
            .start(
                () -> {
                  try (BufferedReader reader =
                      new BufferedReader(
                          new InputStreamReader(
                              startedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                      outputLines.offer(line.trim());
                    }
                  } catch (IOException exception) {
                    if (!closed) {
                      outputLines.offer("info string LastMove reader failure: " + exception);
                    }
                  } finally {
                    outputLines.offer(END_OF_OUTPUT);
                  }
                });
  }

  private String awaitLine(Predicate<String> expected, Duration timeout) {
    long remainingNanos = timeout.toNanos();
    long deadline = System.nanoTime() + remainingNanos;
    try {
      while (remainingNanos > 0) {
        String line = outputLines.poll(remainingNanos, TimeUnit.NANOSECONDS);
        if (line == null) {
          break;
        }
        if (END_OF_OUTPUT.equals(line)) {
          throw new ComputerEngineException(
              "UCI engine " + descriptor().displayName() + " stopped unexpectedly");
        }
        if (expected.test(line)) {
          return line;
        }
        remainingNanos = deadline - System.nanoTime();
      }
      throw new ComputerEngineException(
          "UCI engine " + descriptor().displayName() + " did not respond before the timeout");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ComputerEngineException("Interrupted while waiting for the UCI engine", exception);
    }
  }

  private Optional<MoveCommand> parseBestMoveToken(String response) {
    String[] tokens = response.split("\\s+");
    if (tokens.length < 2 || "(none)".equals(tokens[1]) || "0000".equals(tokens[1])) {
      return Optional.empty();
    }
    return Optional.of(UciMoveParser.parse(tokens[1]));
  }

  private void sendCommand(String command) {
    synchronized (writerMonitor) {
      Process current = process;
      BufferedWriter writer = processInput;
      if (current == null || !current.isAlive() || writer == null) {
        throw new ComputerEngineException("The UCI engine process is not running");
      }
      try {
        writer.write(command);
        writer.newLine();
        writer.flush();
      } catch (IOException exception) {
        throw new ComputerEngineException("Could not send command to the UCI engine", exception);
      }
    }
  }

  private void trySend(String command) {
    try {
      sendCommand(command);
    } catch (ComputerEngineException ignored) {
      // Closing is best-effort; terminateProcess() below remains the final guarantee.
    }
  }

  private void awaitExit(Process current, Duration timeout) {
    try {
      if (!current.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        current.destroy();
        if (!current.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
          current.destroyForcibly();
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      current.destroyForcibly();
    } finally {
      terminateProcess();
    }
  }

  private void terminateProcess() {
    Process current = process;
    if (current != null && current.isAlive()) {
      current.destroyForcibly();
    }
    process = null;
    processInput = null;
    ready = false;
  }

  private long positiveMillis(Duration duration) {
    long millis = duration.toMillis();
    return Math.max(1L, millis);
  }

  private ComputerEngineException closedEngineException() {
    return new ComputerEngineException("The UCI engine has already been closed");
  }
}
