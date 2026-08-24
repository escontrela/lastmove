package com.escontrela.lastmove.ui.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import org.junit.jupiter.api.Test;

class SelectStudyDestinationEventTest {

  @Test
  void defaultsExistingAnalysisRequestsToReturningToAnalysis() {
    SelectStudyDestinationEvent event =
        new SelectStudyDestinationEvent(AnalysisSessionId.random());

    assertEquals(
        SelectStudyDestinationEvent.PostCopyDestination.RETURN_TO_ANALYSIS,
        event.postCopyDestination());
  }

  @Test
  void retainsARequestToOpenTheNewStudyWorkspace() {
    SelectStudyDestinationEvent event =
        new SelectStudyDestinationEvent(
            AnalysisSessionId.random(),
            SelectStudyDestinationEvent.PostCopyDestination.OPEN_STUDY_WORKSPACE);

    assertEquals(
        SelectStudyDestinationEvent.PostCopyDestination.OPEN_STUDY_WORKSPACE,
        event.postCopyDestination());
  }
}
