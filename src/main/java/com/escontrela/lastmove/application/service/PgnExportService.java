package com.escontrela.lastmove.application.service;

import com.escontrela.lastmove.application.repository.AnalysisSessionRepository;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisSession;
import com.escontrela.lastmove.domain.analysis.AnalysisSessionId;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.service.FenService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Serializes one retained analysis session as portable game notation.
 *
 * <p>The preferred first continuation becomes the PGN main line and every sibling continuation is
 * retained as a recursive annotation variation. Studies created from a non-standard position also
 * receive the required {@code SetUp} and {@code FEN} tags.
 */
@Service
public final class PgnExportService {

  private final AnalysisSessionRepository sessionRepository;
  private final FenService fenService;

  public PgnExportService(AnalysisSessionRepository sessionRepository, FenService fenService) {
    this.sessionRepository =
        Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  /** Returns a complete UTF-8-ready PGN document for the selected analysis session. */
  public String export(AnalysisSessionId sessionId) {
    AnalysisSession session =
        sessionRepository
            .findById(Objects.requireNonNull(sessionId, "sessionId must not be null"))
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "No open analysis session with id " + sessionId.value()));
    GameResult result = exportedResult(session);
    String initialFen = fenService.fromSnapshot(session.initialPosition()).getValue();
    StringBuilder pgn = new StringBuilder();
    appendTag(pgn, "Event", session.title());
    appendTag(pgn, "Site", "LastMove");
    appendTag(pgn, "Date", "????.??.??");
    appendTag(pgn, "Round", "-");
    appendTag(pgn, "White", "?");
    appendTag(pgn, "Black", "?");
    appendTag(pgn, "Result", result.getPgn());
    if (!initialFen.equals(Fen.startingPosition().getValue())) {
      appendTag(pgn, "SetUp", "1");
      appendTag(pgn, "FEN", initialFen);
    }
    pgn.append('\n');
    List<AnalysisNode> roots = session.rootVariations();
    if (!roots.isEmpty()) {
      appendLine(pgn, session, roots.getFirst(), roots.subList(1, roots.size()), true);
      pgn.append(' ');
    }
    pgn.append(result.getPgn()).append('\n');
    return pgn.toString();
  }

  private void appendLine(
      StringBuilder target,
      AnalysisSession session,
      AnalysisNode node,
      List<AnalysisNode> alternatives,
      boolean lineStart) {
    appendMove(target, node.ply(), lineStart);
    for (AnalysisNode alternative : alternatives) {
      target.append(" (");
      appendBranch(target, session, alternative);
      target.append(')');
    }
    List<AnalysisNode> continuations = session.continuations(node.id());
    if (!continuations.isEmpty()) {
      target.append(' ');
      appendLine(
          target,
          session,
          continuations.getFirst(),
          continuations.subList(1, continuations.size()),
          false);
    }
  }

  private void appendBranch(
      StringBuilder target, AnalysisSession session, AnalysisNode branchRoot) {
    List<AnalysisNode> continuations = session.continuations(branchRoot.id());
    appendMove(target, branchRoot.ply(), true);
    if (!continuations.isEmpty()) {
      target.append(' ');
      appendLine(
          target,
          session,
          continuations.getFirst(),
          continuations.subList(1, continuations.size()),
          false);
    }
  }

  private void appendMove(StringBuilder target, Ply ply, boolean lineStart) {
    if (ply.movingColor() == PieceColor.WHITE) {
      target.append(ply.moveNumber()).append(". ");
    } else if (lineStart) {
      target.append(ply.moveNumber()).append("... ");
    }
    target.append(ply.move().san().getValue());
  }

  private GameResult exportedResult(AnalysisSession session) {
    return session.sourceResult().orElseGet(() -> terminalPreferredLineResult(session));
  }

  private GameResult terminalPreferredLineResult(AnalysisSession session) {
    Optional<AnalysisNode> node = session.rootVariations().stream().findFirst();
    while (node.isPresent()) {
      AnalysisNode current = node.orElseThrow();
      List<AnalysisNode> children = session.continuations(current.id());
      if (children.isEmpty()) {
        var position = current.ply().resultingPosition();
        if (position.mate()) {
          return position.activeColor() == PieceColor.WHITE
              ? GameResult.BLACK_WINS
              : GameResult.WHITE_WINS;
        }
        return position.stalemate() ? GameResult.DRAW : GameResult.UNKNOWN;
      }
      node = Optional.of(children.getFirst());
    }
    return GameResult.UNKNOWN;
  }

  private void appendTag(StringBuilder target, String name, String value) {
    target
        .append('[')
        .append(name)
        .append(" \"")
        .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
        .append("\"]\n");
  }
}
