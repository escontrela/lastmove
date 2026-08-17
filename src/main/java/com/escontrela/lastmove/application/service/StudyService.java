package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.dto.AnalysisNotationNode;
import com.escontrela.lastmove.application.dto.AnalysisNotationTree;
import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.application.study.CopySessionChapterCommand;
import com.escontrela.lastmove.application.study.CreateChapterCommand;
import com.escontrela.lastmove.application.study.CreateChapterFromFenCommand;
import com.escontrela.lastmove.application.study.CreateStudyCommand;
import com.escontrela.lastmove.application.study.DeleteChapterCommand;
import com.escontrela.lastmove.application.study.DeleteStudyCommand;
import com.escontrela.lastmove.application.study.ImportPgnChapterCommand;
import com.escontrela.lastmove.application.study.MoveChapterCommand;
import com.escontrela.lastmove.application.study.MoveStudyCommand;
import com.escontrela.lastmove.application.study.RenameChapterCommand;
import com.escontrela.lastmove.application.study.RenameStudyCommand;
import com.escontrela.lastmove.application.study.StudyChapterSummary;
import com.escontrela.lastmove.application.study.StudyChapterWorkspace;
import com.escontrela.lastmove.application.study.StudyDetails;
import com.escontrela.lastmove.application.study.StudySummary;
import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.game.ChessGame;
import com.escontrela.lastmove.domain.game.ChessGameFactory;
import com.escontrela.lastmove.domain.game.GameStateSnapshot;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.MoveCommand;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.study.Study;
import com.escontrela.lastmove.domain.study.StudyChapter;
import com.escontrela.lastmove.domain.study.StudyChapterFactory;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.domain.study.StudyRepository;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceAvailability;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Application use case for the persisted study library and its chapter workspaces.
 *
 * <p>Every operation is scoped by an owning {@link PlayerId}: studies are looked up and modified
 * through owner-filtered repository calls, so a caller cannot observe or mutate another player's
 * data. Study persistence requires an available local database; analysis sessions remain
 * independent and are only ever copied into chapters.
 */
@Service
public final class StudyService {

  private final StudyRepository studyRepository;
  private final AnalysisSessionRepository analysisSessionRepository;
  private final StudyChapterFactory chapterFactory;
  private final ChessGameFactory gameFactory;
  private final FenService fenService;
  private final PersistenceAvailability availability;

  public StudyService(
      StudyRepository studyRepository,
      AnalysisSessionRepository analysisSessionRepository,
      StudyChapterFactory chapterFactory,
      ChessGameFactory gameFactory,
      FenService fenService,
      PersistenceAvailability availability) {
    this.studyRepository =
        Objects.requireNonNull(studyRepository, "studyRepository must not be null");
    this.analysisSessionRepository =
        Objects.requireNonNull(analysisSessionRepository, "analysisSessionRepository must not be null");
    this.chapterFactory = Objects.requireNonNull(chapterFactory, "chapterFactory must not be null");
    this.gameFactory = Objects.requireNonNull(gameFactory, "gameFactory must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
    this.availability = Objects.requireNonNull(availability, "availability must not be null");
  }

  /** Creates an empty study owned by the supplied player. */
  public StudySummary createStudy(CreateStudyCommand command) {
    assertAvailable();
    CreateStudyCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = Study.create(required.ownerId(), required.title());
    study.setDescription(required.description());
    return summary(studyRepository.save(study));
  }

  /** Lists the owner's studies in their display order. */
  public List<StudySummary> listStudies(PlayerId ownerId) {
    assertAvailable();
    return studyRepository
        .findAllByOwner(Objects.requireNonNull(ownerId, "ownerId must not be null"))
        .stream()
        .map(this::summary)
        .toList();
  }

  /** Returns one study with its ordered chapters for the owner. */
  public StudyDetails studyDetails(PlayerId ownerId, StudyId studyId) {
    assertAvailable();
    Study study = ownedStudy(ownerId, studyId);
    return new StudyDetails(summary(study), study.chapters().stream().map(this::chapterSummary).toList());
  }

  /** Renames one owned study. */
  public StudySummary renameStudy(RenameStudyCommand command) {
    assertAvailable();
    RenameStudyCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    study.rename(required.title());
    return summary(studyRepository.save(study));
  }

  /** Moves one owned study one place in the owner's library order. */
  public boolean moveStudy(MoveStudyCommand command) {
    assertAvailable();
    MoveStudyCommand required = Objects.requireNonNull(command, "command must not be null");
    List<Study> ordered = studyRepository.findAllByOwner(required.ownerId());
    int currentIndex = indexOf(ordered, required.studyId());
    if (currentIndex < 0) {
      throw unknownStudy(required.studyId());
    }
    int targetIndex = currentIndex + required.offset();
    if (targetIndex < 0 || targetIndex >= ordered.size()) {
      return false;
    }
    return studyRepository.moveStudyToIndex(required.ownerId(), required.studyId(), targetIndex);
  }

  /** Deletes one owned study and all of its chapters. */
  public void deleteStudy(DeleteStudyCommand command) {
    assertAvailable();
    DeleteStudyCommand required = Objects.requireNonNull(command, "command must not be null");
    if (!studyRepository.deleteByIdAndOwner(required.studyId(), required.ownerId())) {
      throw unknownStudy(required.studyId());
    }
  }

  /** Adds a chapter at the standard initial position. */
  public StudyChapterSummary createChapter(CreateChapterCommand command) {
    assertAvailable();
    CreateChapterCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    StudyChapter chapter =
        chapterFactory.fromPosition(
            required.title(),
            AnalysisOrigin.INITIAL_POSITION,
            gameFactory.createAnalysisGame().currentPosition(),
            Optional.empty());
    study.addChapter(chapter);
    studyRepository.save(study);
    return chapterSummary(chapter);
  }

  /** Adds a chapter at the supplied FEN position. */
  public StudyChapterSummary createChapterFromFen(CreateChapterFromFenCommand command) {
    assertAvailable();
    CreateChapterFromFenCommand required =
        Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    StudyChapter chapter =
        chapterFactory.fromPosition(
            required.title(),
            AnalysisOrigin.FEN,
            gameFactory.createAnalysisGame(required.fen()).currentPosition(),
            Optional.empty());
    study.addChapter(chapter);
    studyRepository.save(study);
    return chapterSummary(chapter);
  }

  /** Imports an already parsed PGN game as a new chapter. */
  public StudyChapterSummary importPgnChapter(ImportPgnChapterCommand command) {
    assertAvailable();
    ImportPgnChapterCommand required =
        Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    ImportedPgnGame imported = required.importedGame();
    PositionSnapshot initialPosition =
        imported
            .game()
            .getStartingFen()
            .map(gameFactory::createAnalysisGame)
            .orElseGet(gameFactory::createAnalysisGame)
            .currentPosition();
    StudyChapter chapter =
        chapterFactory.fromImportedPgn(imported.game().displayTitle(), imported, initialPosition);
    study.addChapter(chapter);
    studyRepository.save(study);
    return chapterSummary(chapter);
  }

  /**
   * Archives an independent copy of an ephemeral analysis session as a chapter.
   *
   * <p>The session is read, deep-copied and then left untouched: afterwards the session and the
   * persisted chapter share no mutable state.
   */
  public StudyChapterSummary copySessionChapter(CopySessionChapterCommand command) {
    assertAvailable();
    CopySessionChapterCommand required =
        Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    AnalysisSession session =
        analysisSessionRepository
            .findById(required.sessionId())
            .orElseThrow(
                () -> new NoSuchElementException("No open analysis session with id " + required.sessionId().value()));
    StudyChapter chapter =
        chapterFactory.fromDocument(required.title(), session.origin(), session.document());
    study.addChapter(chapter);
    studyRepository.save(study);
    return chapterSummary(chapter);
  }

  /** Renames one chapter of an owned study. */
  public StudyChapterSummary renameChapter(RenameChapterCommand command) {
    assertAvailable();
    RenameChapterCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    StudyChapter chapter = ownedChapter(study, required.chapterId());
    chapter.rename(required.title());
    studyRepository.save(study);
    return chapterSummary(chapter);
  }

  /** Moves one chapter one place within its study. */
  public boolean moveChapter(MoveChapterCommand command) {
    assertAvailable();
    MoveChapterCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    boolean moved = study.moveChapter(required.chapterId(), required.offset());
    if (moved) {
      studyRepository.save(study);
    }
    return moved;
  }

  /** Deletes one chapter from an owned study. */
  public void deleteChapter(DeleteChapterCommand command) {
    assertAvailable();
    DeleteChapterCommand required = Objects.requireNonNull(command, "command must not be null");
    Study study = ownedStudy(required.ownerId(), required.studyId());
    if (!study.removeChapter(required.chapterId())) {
      throw unknownChapter(required.chapterId());
    }
    studyRepository.save(study);
  }

  /** Returns the renderable workspace of one chapter for the owner. */
  public StudyChapterWorkspace openChapter(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    assertAvailable();
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    return new StudyChapterWorkspace(
        studyId,
        chapter.id(),
        chapter.title(),
        chapter.origin(),
        chapter.document().initialPosition(),
        chapter.document().currentPosition(),
        chapter.document().sourceResult(),
        notationTree(chapter.document()));
  }

  /** Returns the position currently selected by the chapter cursor. */
  public PositionSnapshot currentPosition(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    assertAvailable();
    return ownedChapter(ownedStudy(ownerId, studyId), chapterId).document().currentPosition();
  }

  /** Returns the selected chapter position encoded as complete FEN text. */
  public String currentFen(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    assertAvailable();
    PositionSnapshot position =
        ownedChapter(ownedStudy(ownerId, studyId), chapterId).document().currentPosition();
    return fenService.fromSnapshot(position).getValue();
  }

  /** Returns the rules state derived from the chapter's current position. */
  public GameStateSnapshot gameState(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    assertAvailable();
    return ownedChapter(ownedStudy(ownerId, studyId), chapterId).document().currentState();
  }

  /** Returns the complete visible notation tree with active-route and current-node markers. */
  public AnalysisNotationTree notationTree(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    assertAvailable();
    return notationTree(ownedChapter(ownedStudy(ownerId, studyId), chapterId).document());
  }

  /** Executes a move through {@link ChessGame} and applies its result to the chapter tree. */
  public MoveExecutionResult attemptMove(
      PlayerId ownerId, StudyId studyId, StudyChapterId chapterId, MoveCommand command) {
    assertAvailable();
    Study study = ownedStudy(ownerId, studyId);
    StudyChapter chapter = ownedChapter(study, chapterId);
    ChessGame game = gameFactory.createAnalysisGame(chapter.document().currentPosition());
    MoveExecutionResult result =
        game.move(Objects.requireNonNull(command, "command must not be null"));
    chapter.document().apply(result);
    chapter.touch();
    study.touch();
    studyRepository.save(study);
    return result;
  }

  /** Moves the chapter cursor to the preceding ply and returns the displayed position. */
  public PositionSnapshot previous(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    chapter.document().previous();
    persistNavigation(chapter);
    return chapter.document().currentPosition();
  }

  /** Moves the chapter cursor through its preferred continuation. */
  public PositionSnapshot next(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    chapter.document().next();
    persistNavigation(chapter);
    return chapter.document().currentPosition();
  }

  /** Rewinds the chapter cursor to the initial position without deleting moves. */
  public PositionSnapshot first(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    chapter.document().first();
    persistNavigation(chapter);
    return chapter.document().currentPosition();
  }

  /** Advances the chapter cursor to the final position of its preferred continuation. */
  public PositionSnapshot last(PlayerId ownerId, StudyId studyId, StudyChapterId chapterId) {
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    chapter.document().last();
    persistNavigation(chapter);
    return chapter.document().currentPosition();
  }

  /** Selects a structural analysis node as the chapter cursor. */
  public PositionSnapshot select(
      PlayerId ownerId, StudyId studyId, StudyChapterId chapterId, AnalysisNodeId nodeId) {
    StudyChapter chapter = ownedChapter(ownedStudy(ownerId, studyId), chapterId);
    if (!chapter.document().select(nodeId)) {
      throw new IllegalArgumentException("The node does not belong to chapter " + chapterId.value());
    }
    persistNavigation(chapter);
    return chapter.document().currentPosition();
  }

  private void persistNavigation(StudyChapter chapter) {
    AnalysisDocument document = chapter.document();
    studyRepository.updateChapterNavigation(
        chapter.id(),
        document.navigation(),
        document.navigation().lastVisitedAt().orElseGet(Instant::now));
  }

  private Study ownedStudy(PlayerId ownerId, StudyId studyId) {
    StudyId requiredStudy = Objects.requireNonNull(studyId, "studyId must not be null");
    return studyRepository
        .findByIdAndOwner(requiredStudy, Objects.requireNonNull(ownerId, "ownerId must not be null"))
        .orElseThrow(() -> unknownStudy(requiredStudy));
  }

  private StudyChapter ownedChapter(Study study, StudyChapterId chapterId) {
    return study
        .chapter(Objects.requireNonNull(chapterId, "chapterId must not be null"))
        .orElseThrow(() -> unknownChapter(chapterId));
  }

  private NoSuchElementException unknownStudy(StudyId studyId) {
    return new NoSuchElementException("No study with id " + studyId.value() + " for this player");
  }

  private NoSuchElementException unknownChapter(StudyChapterId chapterId) {
    return new NoSuchElementException("No chapter with id " + chapterId.value());
  }

  private int indexOf(List<Study> ordered, StudyId studyId) {
    for (int index = 0; index < ordered.size(); index++) {
      if (ordered.get(index).id().equals(studyId)) {
        return index;
      }
    }
    return -1;
  }

  private StudySummary summary(Study study) {
    return new StudySummary(
        study.id(),
        study.title(),
        study.description(),
        study.chapters().size(),
        study.createdAt(),
        study.updatedAt());
  }

  private StudyChapterSummary chapterSummary(StudyChapter chapter) {
    return new StudyChapterSummary(
        chapter.id(),
        chapter.title(),
        chapter.origin(),
        moveCount(chapter.document()),
        chapter.createdAt(),
        chapter.updatedAt());
  }

  private int moveCount(AnalysisDocument document) {
    return countNodes(document, document.rootVariations());
  }

  private int countNodes(AnalysisDocument document, List<AnalysisNode> nodes) {
    int count = nodes.size();
    for (AnalysisNode node : nodes) {
      count += countNodes(document, document.continuations(node.id()));
    }
    return count;
  }

  private AnalysisNotationTree notationTree(AnalysisDocument document) {
    Set<AnalysisNodeId> activeNodeIds =
        new HashSet<>(document.notationNodes().stream().map(AnalysisNode::id).toList());
    Optional<AnalysisNodeId> currentNodeId = document.currentNode().map(AnalysisNode::id);
    return new AnalysisNotationTree(
        projectNotationNodes(document, document.rootVariations(), activeNodeIds, currentNodeId),
        currentNodeId);
  }

  private List<AnalysisNotationNode> projectNotationNodes(
      AnalysisDocument document,
      List<AnalysisNode> nodes,
      Set<AnalysisNodeId> activeNodeIds,
      Optional<AnalysisNodeId> currentNodeId) {
    List<AnalysisNotationNode> projected = new ArrayList<>(nodes.size());
    for (int index = 0; index < nodes.size(); index++) {
      AnalysisNode node = nodes.get(index);
      projected.add(
          new AnalysisNotationNode(
              node.id(),
              node.ply(),
              projectNotationNodes(
                  document, document.continuations(node.id()), activeNodeIds, currentNodeId),
              index == 0,
              activeNodeIds.contains(node.id()),
              currentNodeId.filter(node.id()::equals).isPresent()));
    }
    return List.copyOf(projected);
  }

  private void assertAvailable() {
    if (!availability.isAvailable()) {
      throw new PersistenceUnavailableException(
          "Study persistence is unavailable"
              + availability.reason().map(reason -> ": " + reason).orElse(""));
    }
  }
}