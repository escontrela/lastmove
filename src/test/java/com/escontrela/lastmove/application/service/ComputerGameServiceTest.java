package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.application.computer.ComputerGamePhase;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.GameTerminationReason;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.escontrela.lastmove.infrastructure.game.InMemoryProgressiveGameRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionFactory;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.infrastructure.session.InMemoryAnalysisSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class ComputerGameServiceTest {

  private final MutableClock clock =
      new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));
  private final FakeEngineProvider engineProvider = new FakeEngineProvider();
  private final ComputerGameService service =
      new ComputerGameService(
          new InMemoryProgressiveGameRepository(),
          new ChessGameFactory(new ChesspressoRulesEngine()),
          List.of(engineProvider),
          clock);

  @Test
  void coordinatesHumanMoveComputerReplyAndClock() {
    engineProvider.moves.add(move("e7", "e5"));
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    clock.advance(Duration.ofSeconds(12));

    var state =
        service.playHumanMove(created.gameId(), move("e2", "e4")).toCompletableFuture().join();

    assertEquals(ComputerGamePhase.WAITING_FOR_HUMAN, state.phase());
    assertEquals(PieceColor.WHITE, state.gameState().whoseTurn());
    assertEquals(List.of("e4", "e5"), sans(state));
    assertEquals(Duration.ofMinutes(4).plusSeconds(48), state.clock().whiteRemaining().orElseThrow());
    assertTrue(state.canMove());
    assertTrue(state.canTakeBack());
  }

  @Test
  void exposesConfiguredEnginesForTheSetupOverlay() {
    assertEquals(List.of(engineProvider.descriptor), service.availableEngines());
  }

  @Test
  void engineMakesTheOpeningMoveWhenTheHumanSelectedBlack() {
    engineProvider.moves.add(move("e2", "e4"));

    var state = service.createGame(configuration(PieceColor.BLACK)).toCompletableFuture().join();

    assertEquals(List.of("e4"), sans(state));
    assertEquals(PieceColor.BLACK, state.gameState().whoseTurn());
    assertTrue(state.canMove());
  }

  @Test
  void illegalHumanMoveDoesNotStartTheEngineOrMutateHistory() {
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();

    var state =
        service.playHumanMove(created.gameId(), move("e2", "e5")).toCompletableFuture().join();

    assertTrue(state.moves().isEmpty());
    assertEquals(ComputerGamePhase.WAITING_FOR_HUMAN, state.phase());
    assertTrue(state.message().isPresent());
    assertEquals(0, engineProvider.lastEngine.chooseMoveCalls);
  }

  @Test
  void takebackIsAutomaticallyAcceptedAndRestoresTheCompleteHumanTurn() {
    engineProvider.moves.add(move("e7", "e5"));
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    var played =
        service.playHumanMove(created.gameId(), move("e2", "e4")).toCompletableFuture().join();

    var restored = service.takeBack(played.gameId());

    assertTrue(restored.moves().isEmpty());
    assertEquals(PieceColor.WHITE, restored.gameState().whoseTurn());
    assertEquals(ComputerGamePhase.WAITING_FOR_HUMAN, restored.phase());
    assertFalse(restored.canTakeBack());
  }

  @Test
  void resignationFinishesTheGameForTheComputer() {
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();

    var resigned = service.resign(created.gameId());

    assertEquals(GameResult.BLACK_WINS, resigned.result().orElseThrow());
    assertEquals(GameTerminationReason.RESIGNATION, resigned.terminationReason().orElseThrow());
    assertEquals(ComputerGamePhase.FINISHED, resigned.phase());
    assertFalse(resigned.canMove());
  }

  @Test
  void restartReplacesTheGameAndPreservesItsConfiguration() {
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    var previousEngine = engineProvider.lastEngine;

    var restarted = service.restartGame(created.gameId()).toCompletableFuture().join();

    assertNotEquals(created.gameId(), restarted.gameId());
    assertEquals(PieceColor.WHITE, restarted.humanColor());
    assertEquals(ComputerGamePhase.WAITING_FOR_HUMAN, restarted.phase());
    assertTrue(restarted.moves().isEmpty());
    assertFalse(previousEngine.running);
    assertThrows(java.util.NoSuchElementException.class, () -> service.state(created.gameId()));
  }

  @Test
  void pollingStateAdjudicatesAClockTimeout() {
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    clock.advance(Duration.ofMinutes(5));

    var expired = service.state(created.gameId());

    assertEquals(GameResult.BLACK_WINS, expired.result().orElseThrow());
    assertEquals(GameTerminationReason.TIMEOUT, expired.terminationReason().orElseThrow());
    assertEquals(Duration.ZERO, expired.clock().whiteRemaining().orElseThrow());
    assertEquals(ComputerGamePhase.FINISHED, expired.phase());
  }

  @Test
  void exposesAnImmutableRecordForLaterAnalysis() {
    engineProvider.moves.add(move("e7", "e5"));
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    service.playHumanMove(created.gameId(), move("e2", "e4")).toCompletableFuture().join();

    var record = service.gameRecord(created.gameId());

    assertEquals(List.of("e4", "e5"),
        record.moves().stream().map(move -> move.ply().move().san().getValue()).toList());
  }

  @Test
  void completedComputerGameCanBecomeAnIndependentAnalysisSession() {
    engineProvider.moves.add(move("e7", "e5"));
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();
    service.playHumanMove(created.gameId(), move("e2", "e4")).toCompletableFuture().join();
    service.resign(created.gameId());
    AnalysisSessionService analysisService =
        new AnalysisSessionService(
            new InMemoryAnalysisSessionRepository(),
            new ChessGameFactory(new ChesspressoRulesEngine()),
            new AnalysisSessionFactory(),
            new FenService());

    var analysis = analysisService.createFromGame(service.gameRecord(created.gameId()));

    assertEquals(AnalysisOrigin.PLAYED_GAME, analysis.origin());
    assertEquals(GameResult.BLACK_WINS, analysis.sourceResult().orElseThrow());
    assertEquals(
        List.of("e4", "e5"),
        analysisService.notationLine(analysis.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());
    service.closeGame(created.gameId());
    assertEquals(
        List.of("e4", "e5"),
        analysisService.notationLine(analysis.sessionId()).stream()
            .map(ply -> ply.move().san().getValue())
            .toList());
  }

  @Test
  void aLateEngineReplyCannotReapplyAMoveAfterTakeback() {
    engineProvider.moves.add(move("e7", "e5"));
    engineProvider.deferReplies = true;
    var created = service.createGame(configuration(PieceColor.WHITE)).toCompletableFuture().join();

    var pendingReply = service.playHumanMove(created.gameId(), move("e2", "e4"));
    var restored = service.takeBack(created.gameId());
    engineProvider.lastEngine.completePendingMove();
    var finalState = pendingReply.toCompletableFuture().join();

    assertTrue(restored.moves().isEmpty());
    assertTrue(finalState.moves().isEmpty());
    assertEquals(PieceColor.WHITE, finalState.gameState().whoseTurn());
  }

  private ComputerGameConfiguration configuration(PieceColor humanColor) {
    return new ComputerGameConfiguration(
        "Human",
        humanColor,
        TimeControl.of(Duration.ofMinutes(5), Duration.ZERO),
        "fake",
        Duration.ofMillis(100));
  }

  private static List<String> sans(
      com.escontrela.lastmove.application.computer.ComputerGameState state) {
    return state.moves().stream().map(ply -> ply.move().san().getValue()).toList();
  }

  private static MoveCommand move(String from, String to) {
    return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
  }

  private static final class FakeEngineProvider implements ComputerMoveEngineProvider {
    private final ComputerEngineDescriptor descriptor =
        new ComputerEngineDescriptor("fake", "Test engine", "1");
    private final Queue<MoveCommand> moves = new ArrayDeque<>();
    private FakeEngine lastEngine;
    private boolean deferReplies;

    @Override
    public ComputerEngineDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public ComputerMoveEngine create() {
      lastEngine = new FakeEngine(descriptor, moves, deferReplies);
      return lastEngine;
    }
  }

  private static final class FakeEngine implements ComputerMoveEngine {
    private final ComputerEngineDescriptor descriptor;
    private final Queue<MoveCommand> moves;
    private int chooseMoveCalls;
    private boolean running;
    private final boolean deferReplies;
    private CompletableFuture<MoveCommand> pendingMove;
    private MoveCommand deferredMove;

    private FakeEngine(
        ComputerEngineDescriptor descriptor, Queue<MoveCommand> moves, boolean deferReplies) {
      this.descriptor = descriptor;
      this.moves = moves;
      this.deferReplies = deferReplies;
    }

    @Override
    public ComputerEngineDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public CompletionStage<Void> start() {
      running = true;
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isRunning() {
      return running;
    }

    @Override
    public boolean isThinking() {
      return false;
    }

    @Override
    public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
      chooseMoveCalls++;
      MoveCommand move = moves.poll();
      if (move == null) {
        return CompletableFuture.failedFuture(new IllegalStateException("No fake move queued"));
      }
      if (deferReplies) {
        deferredMove = move;
        pendingMove = new CompletableFuture<>();
        return pendingMove;
      }
      return CompletableFuture.completedFuture(move);
    }

    @Override
    public void cancelSearch() {}

    @Override
    public void close() {
      running = false;
    }

    private void completePendingMove() {
      pendingMove.complete(deferredMove);
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
