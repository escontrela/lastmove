package com.escontrela.lastmove.application.tactics;

import com.escontrela.lastmove.domain.common.Square;
import java.util.Optional;

/** Presentation data for a requested training hint. */
public record TacticHint(TacticWorkspace workspace, Optional<Square> sourceSquare) {}
