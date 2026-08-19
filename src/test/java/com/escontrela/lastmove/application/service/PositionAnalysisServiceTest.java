package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineSettings;
import com.escontrela.lastmove.application.computer.ComputerEngineSettingsRepository;
import com.escontrela.lastmove.application.computer.ComputerMoveEngine;
import com.escontrela.lastmove.application.computer.ComputerMoveEngineProvider;
import com.escontrela.lastmove.application.computer.ComputerMoveRequest;
import com.escontrela.lastmove.application.computer.EngineAnalysisResult;
import com.escontrela.lastmove.application.computer.EngineScore;
import com.escontrela.lastmove.application.dto.PositionAnalysisResult;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class PositionAnalysisServiceTest {

  private static final MoveCommand MOVE =
      new MoveCommand(Square.of("e2"), Square.of("e4"), Optional.empty());

  @Test
  void returnsSanAndWhitePerspectiveScoreForTheSelectedEngine() {
    FakeEngineProvider knightshade = new FakeEngineProvider("knightshade");
    knightshade.enqueue(EngineAnalysisResult.of(MOVE, EngineScore.centipawns(35), 12));
    PositionAnalysisService service = service(List.of(knightshade));

    PositionAnalysisResult result =
        service.analyze(position(PieceColor.WHITE), "knightshade").toCompletableFuture().join()
            .orElseThrow();

    assertEquals("Engine knightshade 1.0", result.engineDisplayName());
    assertEquals("Nf3", result.bestMoveSan().orElseThrow());
    assertEquals("+0.35", result.scoreText().orElseThrow());
    assertEquals(Integer.valueOf(12), result.depth().orElseThrow());
  }

  @Test
  void fallsBackToKnightshadeWhenNoDefaultEngineIsConfigured() {
    PositionAnalysisService service =
        service(List.of(new FakeEngineProvider("knightshade"), new FakeEngineProvider("sunfish")));

    assertEquals("knightshade", service.defaultEngineId());
  }

  @Test
  void honoursTheConfiguredDefaultAnalysisEngine() {
    InMemorySettingsRepository repository = new InMemorySettingsRepository();
    repository.saveDefaultAnalysisEngineId("sunfish");
    PositionAnalysisService service =
        service(
            List.of(new FakeEngineProvider("knightshade"), new FakeEngineProvider("sunfish")),
            repository);

    assertEquals("sunfish", service.defaultEngineId());
  }

  @Test
  void discardsASupersededAnalysisResult() {
    FakeEngineProvider knightshade = new FakeEngineProvider("knightshade");
    CompletableFuture<EngineAnalysisResult> first = new CompletableFuture<>();
    CompletableFuture<EngineAnalysisResult> second = new CompletableFuture<>();
    knightshade.enqueue(first);
    knightshade.enqueue(second);
    PositionAnalysisService service = service(List.of(knightshade));

    var firstStage = service.analyze(position(PieceColor.WHITE), "knightshade");
    var secondStage = service.analyze(position(PieceColor.WHITE), "knightshade");

    first.complete(EngineAnalysisResult.moveOnly(MOVE));
    second.complete(EngineAnalysisResult.moveOnly(MOVE));

    assertTrue(firstStage.toCompletableFuture().join().isEmpty());
    assertTrue(secondStage.toCompletableFuture().join().isPresent());
  }

  @Test
  void closesThePreviousEngineWhenSwitchingEngine() {
    FakeEngineProvider knightshade = new FakeEngineProvider("knightshade");
    FakeEngineProvider sunfish = new FakeEngineProvider("sunfish");
    knightshade.enqueue(EngineAnalysisResult.moveOnly(MOVE));
    sunfish.enqueue(EngineAnalysisResult.moveOnly(MOVE));
    PositionAnalysisService service = service(List.of(knightshade, sunfish));

    service.analyze(position(PieceColor.WHITE), "knightshade").toCompletableFuture().join();
    assertFalse(knightshade.created.get(0).isClosed());

    service.analyze(position(PieceColor.WHITE), "sunfish").toCompletableFuture().join();

    assertTrue(knightshade.created.get(0).isClosed());
    assertEquals(1, sunfish.created.size());
  }

  private static PositionAnalysisService service(List<FakeEngineProvider> providers) {
    return service(providers, new InMemorySettingsRepository());
  }

  private static PositionAnalysisService service(
      List<FakeEngineProvider> providers, InMemorySettingsRepository repository) {
    ComputerEngineSettingsService settingsService =
        new ComputerEngineSettingsService(
            repository, "/default/sunfish-uci", "/default/maia");
    return new PositionAnalysisService(
        List.copyOf(providers), new FixedSanRulesEngine(), settingsService);
  }

  private static PositionSnapshot position(PieceColor activeColor) {
    return new PositionSnapshot(
        List.of(), activeColor, Optional.empty(), false, false);
  }

  private static final class FixedSanRulesEngine implements ChessRulesEngine {

    @Override
    public PositionSnapshot startingPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PositionSnapshot positionFrom(Fen fen) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MoveExecutionResult execute(PositionSnapshot currentPosition, MoveCommand command) {
      MoveDescriptor descriptor =
          new MoveDescriptor(
              command.from(),
              command.to(),
              SanMove.of("Nf3"),
              false,
              false,
              false,
              command.promotion());
      return MoveExecutionResult.accepted(currentPosition, descriptor);
    }

    @Override
    public MoveExecutionResult execute(PositionSnapshot currentPosition, SanMove move) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeEngineProvider implements ComputerMoveEngineProvider {

    private final ComputerEngineDescriptor descriptor;
    private final List<FakeEngine> created = new ArrayList<>();
    private final Queue<CompletableFuture<EngineAnalysisResult>> responses = new ArrayDeque<>();

    FakeEngineProvider(String id) {
      this.descriptor = new ComputerEngineDescriptor(id, "Engine " + id, "1.0");
    }

    @Override
    public ComputerEngineDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public ComputerMoveEngine create() {
      FakeEngine engine = new FakeEngine(this);
      created.add(engine);
      return engine;
    }

    void enqueue(EngineAnalysisResult result) {
      responses.add(CompletableFuture.completedFuture(result));
    }

    void enqueue(CompletableFuture<EngineAnalysisResult> response) {
      responses.add(response);
    }

    CompletableFuture<EngineAnalysisResult> nextResponse() {
      return responses.poll();
    }
  }

  private static final class FakeEngine implements ComputerMoveEngine {

    private final FakeEngineProvider provider;
    private boolean closed;

    FakeEngine(FakeEngineProvider provider) {
      this.provider = provider;
    }

    @Override
    public ComputerEngineDescriptor descriptor() {
      return provider.descriptor();
    }

    @Override
    public CompletionStage<Void> start() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isRunning() {
      return !closed;
    }

    @Override
    public boolean isThinking() {
      return false;
    }

    @Override
    public CompletionStage<MoveCommand> chooseMove(ComputerMoveRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<EngineAnalysisResult> analyze(ComputerMoveRequest request) {
      CompletableFuture<EngineAnalysisResult> response = provider.nextResponse();
      if (response == null) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("No queued engine response"));
      }
      return response;
    }

    @Override
    public void cancelSearch() {}

    @Override
    public void close() {
      closed = true;
    }

    boolean isClosed() {
      return closed;
    }
  }

  private static final class InMemorySettingsRepository
      implements ComputerEngineSettingsRepository {

    private final Map<String, ComputerEngineSettings> settings = new HashMap<>();
    private String defaultAnalysisEngineId;

    @Override
    public Optional<ComputerEngineSettings> findByEngineId(String engineId) {
      return Optional.ofNullable(settings.get(engineId));
    }

    @Override
    public void save(ComputerEngineSettings settings) {
      this.settings.put(settings.engineId(), settings);
    }

    @Override
    public void deleteByEngineId(String engineId) {
      settings.remove(engineId);
    }

    @Override
    public Optional<String> findDefaultAnalysisEngineId() {
      return Optional.ofNullable(defaultAnalysisEngineId);
    }

    @Override
    public void saveDefaultAnalysisEngineId(String engineId) {
      this.defaultAnalysisEngineId = engineId;
    }

    @Override
    public void deleteDefaultAnalysisEngineId() {
      this.defaultAnalysisEngineId = null;
    }
  }
}
