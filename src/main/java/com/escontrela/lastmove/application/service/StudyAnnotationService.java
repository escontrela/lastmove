package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.study.StudyAnnotationRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.study.Study;
import com.escontrela.lastmove.domain.study.StudyChapter;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.domain.study.StudyRepository;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Owner-scoped application service for study, chapter and move annotations. */
@Service
public final class StudyAnnotationService {
  private final StudyRepository studies;
  private final StudyAnnotationRepository annotations;

  public StudyAnnotationService(StudyRepository studies, StudyAnnotationRepository annotations) {
    this.studies = Objects.requireNonNull(studies);
    this.annotations = Objects.requireNonNull(annotations);
  }

  public Optional<String> studyComment(PlayerId owner, StudyId studyId) {
    ownedStudy(owner, studyId);
    return annotations.studyComment(studyId);
  }

  public Optional<String> chapterComment(PlayerId owner, StudyId studyId, StudyChapterId chapterId) {
    ownedChapter(owner, studyId, chapterId);
    return annotations.chapterComment(chapterId);
  }

  public Optional<String> moveComment(PlayerId owner, StudyId studyId, StudyChapterId chapterId, AnalysisNodeId nodeId) {
    StudyChapter chapter = ownedChapter(owner, studyId, chapterId);
    requireNode(chapter, nodeId);
    return annotations.moveComment(chapterId, nodeId);
  }

  /** Returns every saved move annotation for an owned chapter in one repository read. */
  public Map<AnalysisNodeId, String> moveComments(
      PlayerId owner, StudyId studyId, StudyChapterId chapterId) {
    ownedChapter(owner, studyId, chapterId);
    return annotations.moveComments(chapterId);
  }

  public void saveStudyComment(PlayerId owner, StudyId studyId, String comment) {
    ownedStudy(owner, studyId);
    annotations.saveStudyComment(studyId, normalize(comment));
  }

  public void saveChapterComment(PlayerId owner, StudyId studyId, StudyChapterId chapterId, String comment) {
    ownedChapter(owner, studyId, chapterId);
    annotations.saveChapterComment(chapterId, normalize(comment));
  }

  public void saveMoveComment(PlayerId owner, StudyId studyId, StudyChapterId chapterId, AnalysisNodeId nodeId, String comment) {
    StudyChapter chapter = ownedChapter(owner, studyId, chapterId);
    requireNode(chapter, nodeId);
    annotations.saveMoveComment(chapterId, nodeId, normalize(comment));
  }

  private Study ownedStudy(PlayerId owner, StudyId studyId) {
    return studies.findByIdAndOwner(studyId, owner)
        .orElseThrow(() -> new NoSuchElementException("Unknown study " + studyId.value()));
  }

  private StudyChapter ownedChapter(PlayerId owner, StudyId studyId, StudyChapterId chapterId) {
    return ownedStudy(owner, studyId).chapter(chapterId)
        .orElseThrow(() -> new NoSuchElementException("Unknown chapter " + chapterId.value()));
  }

  private static void requireNode(StudyChapter chapter, AnalysisNodeId nodeId) {
    if (chapter.document().content().tree().find(nodeId).isEmpty()) {
      throw new NoSuchElementException("Unknown move " + nodeId.value());
    }
  }

  private static String normalize(String comment) {
    return comment == null ? "" : comment.strip();
  }
}
