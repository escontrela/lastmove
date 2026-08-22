package com.escontrela.lastmove.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.player.Player;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.study.Study;
import com.escontrela.lastmove.domain.study.StudyChapter;
import com.escontrela.lastmove.domain.study.StudyChapterFactory;
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

class SqliteStudyRepositoryTest {

    @TempDir
    Path tempDir;

    private SqliteStudyRepository repository;
    private SqlitePlayerRepository playerRepository;
    private ChessGameFactory gameFactory;
    private StudyChapterFactory chapterFactory;
    private ChesspressoRulesEngine rulesEngine;
    private JdbcTemplate jdbcTemplate;
    private PlayerId ownerId;

    @BeforeEach
    void setUp() {
        DataSource dataSource =
                DataSourceBuilder.create()
                        .driverClassName("org.sqlite.JDBC")
                        .url("jdbc:sqlite:" + tempDir.resolve("test.db") + "?foreign_keys=on")
                        .build();
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        PersistenceAvailability availability = PersistenceAvailability.available();
        rulesEngine = new ChesspressoRulesEngine();
        FenService fenService = new FenService();
        repository =
                new SqliteStudyRepository(jdbcTemplate, availability, rulesEngine, fenService);
        playerRepository = new SqlitePlayerRepository(jdbcTemplate, availability);
        gameFactory = new ChessGameFactory(rulesEngine);
        chapterFactory = new StudyChapterFactory(new AnalysisDocumentFactory());
        ownerId =
                playerRepository
                        .save(
                                Player.create(
                                        "owner@example.com", "Owner", "One", Optional.empty()))
                        .id();
    }

    @Test
    void savesAndLoadsStudyWithChaptersMovesAndNavigation() {
        Study study = studyWithChapterMoves("e2", "e4", "e7", "e5");
        study.chapters().getFirst().document().rootVariations().getFirst()
                .setComment("Claims space in the centre");
        repository.save(study);

        Optional<Study> loaded = repository.findByIdAndOwner(study.id(), ownerId);

        assertTrue(loaded.isPresent());
        Study reloaded = loaded.orElseThrow();
        assertEquals(1, reloaded.chapters().size());
        StudyChapter chapter = reloaded.chapters().getFirst();
        assertEquals("Main line", chapter.title());
        assertEquals(1, chapter.document().rootVariations().getFirst().ply().moveNumber());
        assertEquals(
            List.of("e4", "e5"),
            chapter.document().notationLine().stream()
                .map(ply -> ply.move().san().getValue())
                .toList());
        assertEquals(
            "e5",
            chapter.document().currentPly().orElseThrow().move().san().getValue());
        assertEquals(
            "Claims space in the centre",
            chapter.document().rootVariations().getFirst().comment().orElseThrow());
    }

    @Test
    void roundTripsVariationsAndPreferredContinuation() {
        Study study = Study.create(ownerId, "Openings");
        StudyChapter chapter =
            chapterFactory.fromPosition("Sicilian", AnalysisOrigin.INITIAL_POSITION, start(), Optional.empty());
        applyMove(chapter, start(), "e2", "e4");
        AnalysisNode e4 = chapter.document().currentNode().orElseThrow();
        applyMove(chapter, afterMove(start(), "e2", "e4"), "e7", "e5");
        AnalysisNode e5 = chapter.document().currentNode().orElseThrow();
        chapter.document().select(e4.id());
        applyMove(chapter, afterMove(start(), "e2", "e4"), "c7", "c5");
        AnalysisNode c5 = chapter.document().currentNode().orElseThrow();
        chapter.document().select(e5.id());
        study.addChapter(chapter);
        repository.save(study);

        StudyChapter reloaded =
            repository.findByIdAndOwner(study.id(), ownerId).orElseThrow().chapters().getFirst();
        AnalysisNode reloadedE4 = reloaded.document().rootVariations().getFirst();
        assertEquals(
            List.of("e5", "c5"),
            reloaded.document().continuations(reloadedE4.id()).stream()
                .map(node -> node.ply().move().san().getValue())
                .toList());
        assertEquals("e5", reloaded.document().currentPly().orElseThrow().move().san().getValue());
        assertFalse(reloaded.document().currentNode().orElseThrow().id().equals(c5.id()));
    }

    @Test
    void persistsReadingStateWithoutRewritingTheTree() {
        Study study = studyWithChapterMoves("e2", "e4", "e7", "e5");
        repository.save(study);
        Study reloaded = repository.findByIdAndOwner(study.id(), ownerId).orElseThrow();
        StudyChapter chapter = reloaded.chapters().getFirst();
        AnalysisNode e4 = chapter.document().rootVariations().getFirst();
        chapter.document().select(e4.id());

        repository.updateChapterNavigation(chapter.id(), chapter.document().navigation(), java.time.Instant.now());

        StudyChapter refreshed =
            repository.findByIdAndOwner(study.id(), ownerId).orElseThrow().chapters().getFirst();
        assertEquals(e4.id(), refreshed.document().currentNode().orElseThrow().id());
        assertEquals("e4", refreshed.document().currentPly().orElseThrow().move().san().getValue());
    }

    @Test
    void ordersStudiesAndMovesOneToAnIndex() {
        Study first = Study.create(ownerId, "First");
        Study second = Study.create(ownerId, "Second");
        Study third = Study.create(ownerId, "Third");
        repository.save(first);
        repository.save(second);
        repository.save(third);

        assertTrue(repository.moveStudyToIndex(ownerId, third.id(), 0));
        assertEquals(
            List.of("Third", "First", "Second"),
            repository.findAllByOwner(ownerId).stream().map(Study::title).toList());

        assertFalse(repository.moveStudyToIndex(ownerId, second.id(), 3));
    }

    @Test
    void ownerScopesReadsAndDeletes() {
        PlayerId otherOwner =
            playerRepository
                .save(Player.create("other@example.com", "Other", "Two", Optional.empty()))
                .id();
        Study study = Study.create(ownerId, "Private");
        repository.save(study);

        assertTrue(repository.findByIdAndOwner(study.id(), otherOwner).isEmpty());
        assertFalse(repository.deleteByIdAndOwner(study.id(), otherOwner));
        assertTrue(repository.findByIdAndOwner(study.id(), ownerId).isPresent());
        assertTrue(repository.deleteByIdAndOwner(study.id(), ownerId));
        assertTrue(repository.findByIdAndOwner(study.id(), ownerId).isEmpty());
    }

    @Test
    void deletingOwnerRemovesTheirStudies() {
        Study study = studyWithChapterMoves("e2", "e4", "e7", "e5");
        repository.save(study);

        repository.deleteByOwner(ownerId);

        assertTrue(repository.findAllByOwner(ownerId).isEmpty());
    }

    @Test
    void deletingAStudyCascadesItsChaptersAndNodes() {
        Study study = studyWithChapterMoves("e2", "e4", "e7", "e5");
        repository.save(study);

        repository.deleteByIdAndOwner(study.id(), ownerId);

        assertTrue(repository.findByIdAndOwner(study.id(), ownerId).isEmpty());
        Integer nodes =
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM study_chapter_nodes WHERE chapter_id = ?",
                Integer.class,
                study.chapters().getFirst().id().value().toString());
        assertEquals(0, nodes);
    }

    private Study studyWithChapterMoves(String from1, String to1, String from2, String to2) {
        Study study = Study.create(ownerId, "Repertoire");
        StudyChapter chapter =
            chapterFactory.fromPosition("Main line", AnalysisOrigin.INITIAL_POSITION, start(), Optional.empty());
        applyMove(chapter, start(), from1, to1);
        applyMove(chapter, afterMove(start(), from1, to1), from2, to2);
        study.addChapter(chapter);
        return study;
    }

    private void applyMove(StudyChapter chapter, PositionSnapshot position, String from, String to) {
        ChessGame game = gameFactory.createAnalysisGame(position);
        MoveExecutionResult result =
            game.move(new MoveCommand(Square.of(from), Square.of(to), Optional.empty()));
        chapter.document().apply(result);
    }

    private PositionSnapshot start() {
        return rulesEngine.startingPosition();
    }

    private PositionSnapshot afterMove(PositionSnapshot position, String from, String to) {
        ChessGame game = gameFactory.createAnalysisGame(position);
        MoveExecutionResult result =
            game.move(new MoveCommand(Square.of(from), Square.of(to), Optional.empty()));
        return result.newSnapshot();
    }
}
