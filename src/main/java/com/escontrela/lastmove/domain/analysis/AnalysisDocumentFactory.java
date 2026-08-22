package com.escontrela.lastmove.domain.analysis;

import com.escontrela.lastmove.domain.game.GameRecord;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.ImportedPgnGame;
import com.escontrela.lastmove.domain.game.ImportedPly;
import com.escontrela.lastmove.domain.game.MoveExecutionResult;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain factory that builds independent {@link AnalysisDocument} values.
 *
 * <p>PGN trees, played-game lines and position-only studies are produced here so sessions and
 * persisted chapters share one construction path. {@link #copyOf} deep-copies a document with fresh
 * node and ply identities so the result never shares mutable state with its source.
 */
public final class AnalysisDocumentFactory {

  /** Creates a document at the supplied position without any move or variation. */
  public AnalysisDocument fromPosition(PositionSnapshot initialPosition, Optional<GameResult> sourceResult) {
    return new AnalysisDocument(
        Objects.requireNonNull(initialPosition, "initialPosition must not be null"),
        Objects.requireNonNull(sourceResult, "sourceResult must not be null"));
  }

  /**
   * Creates a document from an immutable progressive-game record.
   *
   * <p>The official game line becomes the preferred analysis line. New variations added to the
   * resulting document never mutate the source {@link GameRecord}.
   */
  public AnalysisDocument fromGame(GameRecord gameRecord) {
    GameRecord record = Objects.requireNonNull(gameRecord, "gameRecord must not be null");
    AnalysisDocument document = new AnalysisDocument(record.initialPosition(), record.result());
    record.moves().forEach(
        recorded ->
            document.apply(
                MoveExecutionResult.accepted(
                    recorded.ply().resultingPosition(), recorded.ply().move())));
    document.first();
    if (!record.moves().isEmpty()) {
      document.next();
    }
    return document;
  }

  /** Creates a document from an imported PGN move tree at the supplied initial position. */
  public AnalysisDocument fromImportedPgn(ImportedPgnGame importedGame, PositionSnapshot initialPosition) {
    ImportedPgnGame required = Objects.requireNonNull(importedGame, "importedGame must not be null");
    Optional<GameResult> sourceResult =
        required.game().getResult() == GameResult.UNKNOWN
            ? Optional.empty()
            : Optional.of(required.game().getResult());
    AnalysisDocument document = new AnalysisDocument(initialPosition, sourceResult);
    importVariations(document, required.rootVariations());
    document.selectPreferredLine();
    document.first();
    if (!required.rootVariations().isEmpty()) {
      document.next();
    }
    return document;
  }

  /**
   * Deep-copies a document into an independent value.
   *
   * <p>Every node and ply receives a fresh identity and the reading state is remapped onto the new
   * tree. Chess values (positions, move descriptors, results) are immutable and are shared by
   * value, never by reference to mutable state.
   */
  public AnalysisDocument copyOf(AnalysisDocument source) {
    AnalysisDocument required = Objects.requireNonNull(source, "source must not be null");
    AnalysisTree newTree = new AnalysisTree();
    Map<AnalysisNodeId, AnalysisNodeId> remapping = new HashMap<>();
    for (AnalysisNode root : required.content().tree().roots()) {
      copySubtree(required, root, null, newTree, remapping);
    }
    ChapterNavigation navigation = required.navigation().remapped(remapping);
    return new AnalysisDocument(
        new AnalysisContent(required.initialPosition(), required.sourceResult(), newTree),
        navigation);
  }

  private AnalysisNodeId copySubtree(
      AnalysisDocument source,
      AnalysisNode node,
      AnalysisNodeId newParent,
      AnalysisTree destination,
      Map<AnalysisNodeId, AnalysisNodeId> remapping) {
    Ply originalPly = node.ply();
    Ply newPly =
        new Ply(
            UUID.randomUUID(),
            originalPly.move(),
            originalPly.resultingPosition(),
            originalPly.moveNumber(),
            originalPly.movingColor());
    AnalysisNode newNode =
        newParent == null ? destination.addRoot(newPly) : destination.addChild(newParent, newPly);
    node.comment().ifPresent(newNode::setComment);
    remapping.put(node.id(), newNode.id());
    for (AnalysisNode child : source.continuations(node.id())) {
      copySubtree(source, child, newNode.id(), destination, remapping);
    }
    return newNode.id();
  }

  private void importVariations(AnalysisDocument document, List<ImportedPly> variations) {
    for (ImportedPly variation : variations) {
      document.apply(variation.execution());
      importVariations(document, variation.variations());
      document.previous();
    }
  }
}
