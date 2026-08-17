package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.tactics.TacticExercise;
import com.escontrela.lastmove.domain.tactics.TacticExerciseFactory;
import com.escontrela.lastmove.domain.tactics.TacticSuite;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteTacticRepositoryTest {

  @TempDir Path tempDir;

  private SqliteTacticRepository repository;
  private SqlitePlayerRepository playerRepository;
  private ChesspressoRulesEngine rulesEngine;
  private ChessGameFactory gameFactory;
  private TacticExerciseFactory exerciseFactory;
  private PlayerId ownerId;

  @BeforeEach
  void setUp() {
    DataSource dataSource = DataSourceBuilder.create().driverClassName("org.sqlite.JDBC")
        .url("jdbc:sqlite:" + tempDir.resolve("test.db") + "?foreign_keys=on").build();
    Flyway.configure().dataSource(dataSource).load().migrate();
    PersistenceAvailability availability = PersistenceAvailability.available();
    rulesEngine = new ChesspressoRulesEngine();
    FenService fenService = new FenService();
    repository = new SqliteTacticRepository(new JdbcTemplate(dataSource), availability, rulesEngine, fenService);
    playerRepository = new SqlitePlayerRepository(new JdbcTemplate(dataSource), availability);
    gameFactory = new ChessGameFactory(rulesEngine);
    exerciseFactory = new TacticExerciseFactory(new AnalysisDocumentFactory());
    ownerId = playerRepository.save(Player.create("owner@example.com", "Owner", "One", Optional.empty())).id();
  }

  @Test
  void savesAndLoadsOrderedExercisesWithTheirSolutionTree() {
    TacticSuite suite = TacticSuite.create(ownerId, "Opening tactics");
    suite.setDescription(Optional.of("Short forcing lines"));
    suite.addExercise(exerciseFromLine("Win the pawn", "e2", "e4", "e7", "e5"));
    suite.addExercise(exerciseFromLine("Develop", "d2", "d4", "d7", "d5"));
    repository.save(suite);

    TacticSuite loaded = repository.findByIdAndOwner(suite.id(), ownerId).orElseThrow();

    assertEquals("Short forcing lines", loaded.description().orElseThrow());
    assertEquals(List.of("Win the pawn", "Develop"), loaded.exercises().stream().map(TacticExercise::title).toList());
    TacticExercise exercise = loaded.exercises().getFirst();
    assertEquals(PieceColor.WHITE, exercise.solverColor());
    assertEquals(List.of("e4"), exercise.solution().tree().roots().stream().map(node -> node.ply().move().san().getValue()).toList());
    assertEquals(List.of("e5"), exercise.solution().tree().children(exercise.solution().tree().roots().getFirst().id()).stream().map(node -> node.ply().move().san().getValue()).toList());
  }

  @Test
  void scopesAndOrdersSuitesByOwner() {
    PlayerId otherOwner = playerRepository.save(Player.create("other@example.com", "Other", "Two", Optional.empty())).id();
    TacticSuite first = TacticSuite.create(ownerId, "First");
    TacticSuite second = TacticSuite.create(ownerId, "Second");
    TacticSuite privateSuite = TacticSuite.create(otherOwner, "Private");
    repository.save(first);
    repository.save(second);
    repository.save(privateSuite);

    assertTrue(repository.moveSuiteToIndex(ownerId, second.id(), 0));
    assertEquals(List.of("Second", "First"), repository.findAllByOwner(ownerId).stream().map(TacticSuite::title).toList());
    assertTrue(repository.findByIdAndOwner(privateSuite.id(), ownerId).isEmpty());
    assertFalse(repository.deleteByIdAndOwner(privateSuite.id(), ownerId));
  }

  private TacticExercise exerciseFromLine(String title, String firstFrom, String firstTo, String secondFrom, String secondTo) {
    PositionSnapshot start = rulesEngine.startingPosition();
    AnalysisDocument document = new AnalysisDocumentFactory().fromPosition(start, Optional.empty());
    document.apply(move(start, firstFrom, firstTo));
    document.apply(move(after(start, firstFrom, firstTo), secondFrom, secondTo));
    return exerciseFactory.fromDocument(title, document);
  }

  private com.escontrela.lastmove.domain.game.MoveExecutionResult move(PositionSnapshot position, String from, String to) {
    ChessGame game = gameFactory.createAnalysisGame(position);
    return game.move(new MoveCommand(Square.of(from), Square.of(to), Optional.empty()));
  }

  private PositionSnapshot after(PositionSnapshot position, String from, String to) {
    return move(position, from, to).newSnapshot();
  }
}
