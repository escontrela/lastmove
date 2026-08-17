package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import java.util.Optional;

/** Result of adding one move to an authored solution line. */
public record TacticAuthoringMoveOutcome(
    TacticWorkspace workspace, Optional<AnalysisNodeId> nodeId, boolean accepted) {}
