package com.escontrela.lastmove.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.escontrela.lastmove.application.study.CopySessionChapterCommand;
import com.escontrela.lastmove.application.study.CreateChapterCommand;
import com.escontrela.lastmove.application.study.CreateChapterFromFenCommand;
import com.escontrela.lastmove.application.study.CreateStudyCommand;
import com.escontrela.lastmove.application.study.DeleteChapterCommand;
import com.escontrela.lastmove.application.study.DeleteStudyCommand;
import com.escontrela.lastmove.application.study.ImportPgnChapterCommand;
import com.escontrela.lastmove.application.study.MoveChapterCommand;
import com.escontrela.lastmove.application.study.RenameChapterCommand;
import com.escontrela.lastmove.application.study.StudyChapterSummary;
import com.escontrela.lastmove.application.study.StudyDetails;
import com.escontrela.lastmove.application.study.StudySummary;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.study.Study;
import com.escontrela.lastmove.domain.study.StudyChapter;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.domain.study.StudyRepository;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoPgnReader;
import com.escontrela.lastmove.infrastructure.chesspresso.ChesspressoRulesEngine;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import com.escontrela.lastmove.infrastructure.session.InMemoryAnalysisSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudyServiceTest {

    private final FakeStudyRepository studyRepository = new FakeStudyRepository();
    private final InMemoryAnalysisSessionRepository sessionRepository =
        new InMemoryAnalysisSessionRepository();
    private final ChessGameFactory gameFactory = new ChessGameFactory(new ChesspressoRulesEngine());
    private final StudyService service =
        new StudyService(
            studyRepository,
            sessionRepository,
            new com.escontrela.lastmove.domain.study.StudyChapterFactory(
                new com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory()),
            gameFactory,
            new FenService(),
            PersistenceAvailability.available());

    private final PlayerId owner = PlayerId.of(1L);
    private final PlayerId other = PlayerId.of(2L);

    @BeforeEach
    void setUp() {}

    @Test
    void createsAndListsStudiesForTheOwner() {
        StudySummary created =
            service.createStudy(new CreateStudyCommand(owner, "My repertoire", Optional.of("Openings")));

        assertEquals("My repertoire", created.title());
        assertEquals(Optional.of("Openings"), created.description());
        assertEquals(List.of(created.studyId()), service.listStudies(owner).stream().map(StudySummary::studyId).toList());
        assertTrue(service.listStudies(other).isEmpty());
    }

    @Test
    void studyDetailsExposeOrderedChapters() {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Repertoire", Optional.empty())).studyId();
        service.createChapter(new CreateChapterCommand(owner, studyId, "White"));
        service.createChapter(new CreateChapterCommand(owner, studyId, "Black"));
        service.createChapter(new CreateChapterCommand(owner, studyId, "Endings"));

        StudyDetails details = service.studyDetails(owner, studyId);

        assertEquals(
            List.of("White", "Black", "Endings"),
            details.chapters().stream().map(StudyChapterSummary::title).toList());
        assertEquals(3, details.study().chapterCount());
    }

    @Test
    void createsChapterFromFen() {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Tactics", Optional.empty())).studyId();

        StudyChapterSummary chapter =
            service.createChapterFromFen(
                new CreateChapterFromFenCommand(owner, studyId, "Kings only", Fen.of("8/8/8/8/8/8/8/K6k b - - 7 42")));

        assertEquals(AnalysisOrigin.FEN, chapter.origin());
        assertEquals(
            PieceColor.BLACK,
            service.currentPosition(owner, studyId, chapter.chapterId()).activeColor());
    }

    @Test
    void importsPgnAsChapterAndNavigatesIt() throws Exception {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Repertoire", Optional.empty())).studyId();
        var imported =
            new ChesspressoPgnReader()
                .readImportedFirst("[Event \"Main line\"]\n\n1. e4 e5 2. Nf3 Nc6 *");

        StudyChapterSummary chapter =
            service.importPgnChapter(new ImportPgnChapterCommand(owner, studyId, imported));

        assertEquals(AnalysisOrigin.PGN, chapter.origin());
        assertEquals(4, chapter.moveCount());
        assertEquals(
            "e4",
            service.notationTree(owner, studyId, chapter.chapterId()).roots().getFirst().ply().move().san().getValue());
    }

    @Test
    void copyingASessionArchivesAnIndependentChapter() {
        AnalysisSession session =
            new AnalysisSession(
                AnalysisSessionId.random(),
                "Ephemeral ideas",
                AnalysisOrigin.INITIAL_POSITION,
                gameFactory.createAnalysisGame().currentPosition());
        session.apply(acceptedMove(gameFactory, session.currentPosition(), "e2", "e4"));
        session.currentNode().orElseThrow().setComment("King pawn opening");
        sessionRepository.save(session);
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Archive", Optional.empty())).studyId();

        StudyChapterSummary chapter =
            service.copySessionChapter(
                new CopySessionChapterCommand(owner, studyId, "Archived", session.id()));

        assertEquals("Archived", chapter.title());
        assertEquals(1, chapter.moveCount());
        assertEquals(
            "King pawn opening",
            studyRepository.findByIdAndOwner(studyId, owner).orElseThrow()
                .chapter(chapter.chapterId()).orElseThrow()
                .document().rootVariations().getFirst().comment().orElseThrow());

        service.attemptMove(
            owner,
            studyId,
            chapter.chapterId(),
            move("e7", "e5"));
        assertEquals(
            2,
            service.studyDetails(owner, studyId).chapters().getFirst().moveCount());

        assertEquals(1, session.rootVariations().size());
        assertEquals(0, session.continuations(session.rootVariations().getFirst().id()).size());
    }

    @Test
    void renamesMovesAndDeletesChaptersWithinAStudy() {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Study", Optional.empty())).studyId();
        StudyChapterSummary first = service.createChapter(new CreateChapterCommand(owner, studyId, "One"));
        StudyChapterSummary second = service.createChapter(new CreateChapterCommand(owner, studyId, "Two"));

        service.renameChapter(new RenameChapterCommand(owner, studyId, second.chapterId(), "Two renamed"));
        assertTrue(service.moveChapter(new MoveChapterCommand(owner, studyId, second.chapterId(), -1)));
        assertFalse(service.moveChapter(new MoveChapterCommand(owner, studyId, second.chapterId(), -1)));

        StudyDetails details = service.studyDetails(owner, studyId);
        assertEquals(
            List.of("Two renamed", "One"),
            details.chapters().stream().map(StudyChapterSummary::title).toList());

        assertTrue(service.moveChapter(new MoveChapterCommand(owner, studyId, second.chapterId(), 1)));
        service.deleteChapter(new DeleteChapterCommand(owner, studyId, second.chapterId()));
        assertEquals(
            List.of("One"),
            service.studyDetails(owner, studyId).chapters().stream().map(StudyChapterSummary::title).toList());
    }

    @Test
    void rejectsAccessToAnotherPlayersStudy() {
        StudyId otherStudyId =
            service.createStudy(new CreateStudyCommand(other, "Private", Optional.empty())).studyId();

        assertTrue(service.listStudies(owner).isEmpty());
        assertThrows(
            NoSuchElementException.class,
            () -> service.studyDetails(owner, otherStudyId));
        assertThrows(
            NoSuchElementException.class,
            () ->
                service.renameStudy(
                    new com.escontrela.lastmove.application.study.RenameStudyCommand(
                        owner, otherStudyId, "Stolen")));
        assertThrows(
            NoSuchElementException.class,
            () -> service.deleteStudy(new DeleteStudyCommand(owner, otherStudyId)));
    }

    @Test
    void appliesMovesAndNavigatesWithinTheChapter() {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Tactics", Optional.empty())).studyId();
        StudyChapterSummary chapter = service.createChapter(new CreateChapterCommand(owner, studyId, "Line"));

        assertTrue(service.attemptMove(owner, studyId, chapter.chapterId(), move("e2", "e4")).accepted());
        assertEquals(PieceColor.BLACK, service.currentPosition(owner, studyId, chapter.chapterId()).activeColor());

        service.previous(owner, studyId, chapter.chapterId());
        assertEquals(PieceColor.WHITE, service.currentPosition(owner, studyId, chapter.chapterId()).activeColor());
        service.next(owner, studyId, chapter.chapterId());
        assertEquals(PieceColor.BLACK, service.currentPosition(owner, studyId, chapter.chapterId()).activeColor());
    }

    @Test
    void navigationSurvivesReloadThroughTheRepository() {
        StudyId studyId = service.createStudy(new CreateStudyCommand(owner, "Tactics", Optional.empty())).studyId();
        StudyChapterSummary chapter = service.createChapter(new CreateChapterCommand(owner, studyId, "Line"));
        service.attemptMove(owner, studyId, chapter.chapterId(), move("e2", "e4"));
        service.previous(owner, studyId, chapter.chapterId());
        service.next(owner, studyId, chapter.chapterId());

        Study reloaded = studyRepository.findByIdAndOwner(studyId, owner).orElseThrow();
        StudyChapter reloadedChapter = reloaded.chapters().getFirst();
        assertEquals(
            "e4",
            reloadedChapter.document().currentPly().orElseThrow().move().san().getValue());
    }

    @Test
    void throwsWhenPersistenceIsUnavailable() {
        StudyService unavailableService =
            new StudyService(
                studyRepository,
                sessionRepository,
                new com.escontrela.lastmove.domain.study.StudyChapterFactory(
                    new com.escontrela.lastmove.domain.analysis.AnalysisDocumentFactory()),
                gameFactory,
                new FenService(),
                PersistenceAvailability.unavailable("disk full"));

        assertThrows(
            PersistenceUnavailableException.class,
            () -> unavailableService.listStudies(owner));
        assertThrows(
            PersistenceUnavailableException.class,
            () -> unavailableService.createStudy(new CreateStudyCommand(owner, "X", Optional.empty())));
    }

    private MoveCommand move(String from, String to) {
        return new MoveCommand(Square.of(from), Square.of(to), Optional.empty());
    }

    private MoveExecutionResult acceptedMove(
        ChessGameFactory factory, com.escontrela.lastmove.domain.game.PositionSnapshot position, String from, String to) {
        return factory.createAnalysisGame(position).move(move(from, to));
    }

    private static final class FakeStudyRepository implements StudyRepository {

        private final Map<PlayerId, List<Study>> studiesByOwner = new java.util.LinkedHashMap<>();

        @Override
        public Study save(Study study) {
            List<Study> owned = studiesByOwner.computeIfAbsent(study.ownerId(), ignored -> new ArrayList<>());
            owned.removeIf(existing -> existing.id().equals(study.id()));
            owned.add(study);
            return study;
        }

        @Override
        public Optional<Study> findByIdAndOwner(StudyId studyId, PlayerId ownerId) {
            return studiesByOwner.getOrDefault(ownerId, List.of()).stream()
                .filter(study -> study.id().equals(studyId))
                .findFirst();
        }

        @Override
        public List<Study> findAllByOwner(PlayerId ownerId) {
            return List.copyOf(studiesByOwner.getOrDefault(ownerId, List.of()));
        }

        @Override
        public boolean deleteByIdAndOwner(StudyId studyId, PlayerId ownerId) {
            return studiesByOwner
                .computeIfAbsent(ownerId, ignored -> new ArrayList<>())
                .removeIf(study -> study.id().equals(studyId));
        }

        @Override
        public void deleteByOwner(PlayerId ownerId) {
            studiesByOwner.remove(ownerId);
        }

        @Override
        public boolean moveStudyToIndex(PlayerId ownerId, StudyId studyId, int targetIndex) {
            List<Study> owned = new ArrayList<>(studiesByOwner.getOrDefault(ownerId, List.of()));
            int currentIndex = -1;
            for (int index = 0; index < owned.size(); index++) {
              if (owned.get(index).id().equals(studyId)) {
                currentIndex = index;
                break;
              }
            }
            if (currentIndex < 0 || targetIndex < 0 || targetIndex >= owned.size()) {
                return false;
            }
            Study moved = owned.remove(currentIndex);
            owned.add(targetIndex, moved);
            studiesByOwner.put(ownerId, owned);
            return true;
        }

        @Override
        public void updateChapterNavigation(
            com.escontrela.lastmove.domain.study.StudyChapterId chapterId,
            com.escontrela.lastmove.domain.analysis.ChapterNavigation navigation,
            Instant updatedAt) {
            // The fake stores the shared aggregate instance, so the mutation is already visible.
        }
    }
}
