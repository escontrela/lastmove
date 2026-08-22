package com.escontrela.lastmove.application.study;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import java.util.Optional;

/** Persistence boundary for lightweight text annotations attached to study content. */
public interface StudyAnnotationRepository {
  Optional<String> studyComment(StudyId studyId);
  Optional<String> chapterComment(StudyChapterId chapterId);
  Optional<String> moveComment(StudyChapterId chapterId, AnalysisNodeId nodeId);
  void saveStudyComment(StudyId studyId, String comment);
  void saveChapterComment(StudyChapterId chapterId, String comment);
  void saveMoveComment(StudyChapterId chapterId, AnalysisNodeId nodeId, String comment);
}
