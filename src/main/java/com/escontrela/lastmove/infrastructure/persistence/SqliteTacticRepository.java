package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.analysis.AnalysisTree;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.tactics.TacticExercise;
import com.escontrela.lastmove.domain.tactics.TacticExerciseId;
import com.escontrela.lastmove.domain.tactics.TacticExerciseReference;
import com.escontrela.lastmove.domain.tactics.TacticRepository;
import com.escontrela.lastmove.domain.tactics.TacticSuite;
import com.escontrela.lastmove.domain.tactics.TacticSuiteId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQLite persistence for tactic suites and their accepted solution trees. */
@Repository
public class SqliteTacticRepository implements TacticRepository {

  private final JdbcTemplate jdbcTemplate;
  private final PersistenceAvailability availability;
  private final ChessRulesEngine rulesEngine;
  private final FenService fenService;

  public SqliteTacticRepository(
      JdbcTemplate jdbcTemplate,
      PersistenceAvailability availability,
      ChessRulesEngine rulesEngine,
      FenService fenService) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.availability = Objects.requireNonNull(availability, "availability must not be null");
    this.rulesEngine = Objects.requireNonNull(rulesEngine, "rulesEngine must not be null");
    this.fenService = Objects.requireNonNull(fenService, "fenService must not be null");
  }

  @Override
  public TacticSuite save(TacticSuite suite) {
    assertAvailable();
    Objects.requireNonNull(suite, "suite must not be null");
    String suiteId = suite.id().value().toString();
    Integer existingOrder =
        jdbcTemplate.query(
                "SELECT display_order FROM tactic_suites WHERE id = ?",
                (resultSet, rowNum) -> resultSet.getInt(1),
                suiteId)
            .stream()
            .findFirst()
            .orElse(null);
    if (existingOrder == null) {
      jdbcTemplate.update(
          "INSERT INTO tactic_suites (id, owner_player_id, title, description, display_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          suiteId, suite.ownerId().value(), suite.title(), suite.description().orElse(null),
          nextSuiteOrder(suite.ownerId()), suite.createdAt().toEpochMilli(), suite.updatedAt().toEpochMilli());
    } else {
      jdbcTemplate.update(
          "UPDATE tactic_suites SET title = ?, description = ?, updated_at = ? WHERE id = ?",
          suite.title(), suite.description().orElse(null), suite.updatedAt().toEpochMilli(), suiteId);
    }

    jdbcTemplate.update(
        "DELETE FROM tactic_solution_nodes WHERE exercise_id IN (SELECT id FROM tactic_exercises WHERE suite_id = ?)", suiteId);
    jdbcTemplate.update("DELETE FROM tactic_exercises WHERE suite_id = ?", suiteId);
    for (int index = 0; index < suite.exercises().size(); index++) {
      insertExercise(suiteId, suite.exercises().get(index), index);
    }
    return suite;
  }

  @Override
  public Optional<TacticSuite> findByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId) {
    assertAvailable();
    return jdbcTemplate
        .query(
            "SELECT id, owner_player_id, title, description, created_at, updated_at FROM tactic_suites WHERE id = ? AND owner_player_id = ?",
            (resultSet, rowNum) -> suiteRow(resultSet.getString("id"), resultSet.getLong("owner_player_id"), resultSet.getString("title"), resultSet.getString("description"), resultSet.getLong("created_at"), resultSet.getLong("updated_at")),
            suiteId.value().toString(), ownerId.value())
        .stream()
        .findFirst()
        .map(this::hydrateExercises);
  }

  @Override
  public List<TacticSuite> findAllByOwner(PlayerId ownerId) {
    assertAvailable();
    return jdbcTemplate
        .query(
            "SELECT id, owner_player_id, title, description, created_at, updated_at FROM tactic_suites WHERE owner_player_id = ? ORDER BY display_order",
            (resultSet, rowNum) -> suiteRow(resultSet.getString("id"), resultSet.getLong("owner_player_id"), resultSet.getString("title"), resultSet.getString("description"), resultSet.getLong("created_at"), resultSet.getLong("updated_at")),
            ownerId.value())
        .stream()
        .map(this::hydrateExercises)
        .toList();
  }

  @Override
  public List<TacticExerciseReference> findAllTrainableExercises() {
    assertAvailable();
    List<SuiteRow> suites = jdbcTemplate.query(
        "SELECT id, owner_player_id, title, description, created_at, updated_at "
            + "FROM tactic_suites ORDER BY owner_player_id, display_order",
        (resultSet, rowNum) -> new SuiteRow(
            resultSet.getString("id"), resultSet.getLong("owner_player_id"),
            resultSet.getString("title"), resultSet.getString("description"),
            resultSet.getLong("created_at"), resultSet.getLong("updated_at")));
    List<TacticExerciseReference> result = new ArrayList<>();
    for (SuiteRow row : suites) {
      TacticSuiteId suiteId = new TacticSuiteId(UUID.fromString(row.id));
      List<String> exerciseIds = jdbcTemplate.query(
          "SELECT id FROM tactic_exercises WHERE suite_id = ? ORDER BY display_order",
          (resultSet, rowNum) -> resultSet.getString(1), row.id);
      for (String exerciseId : exerciseIds) {
        try {
          TacticExercise exercise = restoreExerciseRow(exerciseId);
          result.add(new TacticExerciseReference(PlayerId.of(row.ownerId), suiteId, exercise));
        } catch (RuntimeException ignored) {
          // A single malformed persisted exercise must not make global training unavailable.
        }
      }
    }
    return List.copyOf(result);
  }

  @Override
  public boolean deleteByIdAndOwner(TacticSuiteId suiteId, PlayerId ownerId) {
    assertAvailable();
    String id = suiteId.value().toString();
    jdbcTemplate.update(
        "DELETE FROM tactic_solution_nodes WHERE exercise_id IN (SELECT id FROM tactic_exercises WHERE suite_id = ?)", id);
    jdbcTemplate.update("DELETE FROM tactic_exercises WHERE suite_id = ?", id);
    return jdbcTemplate.update("DELETE FROM tactic_suites WHERE id = ? AND owner_player_id = ?", id, ownerId.value()) > 0;
  }

  @Override
  public void deleteByOwner(PlayerId ownerId) {
    assertAvailable();
    Long id = ownerId.value();
    jdbcTemplate.update("DELETE FROM tactic_solution_nodes WHERE exercise_id IN (SELECT e.id FROM tactic_exercises e JOIN tactic_suites s ON s.id = e.suite_id WHERE s.owner_player_id = ?)", id);
    jdbcTemplate.update("DELETE FROM tactic_exercises WHERE suite_id IN (SELECT id FROM tactic_suites WHERE owner_player_id = ?)", id);
    jdbcTemplate.update("DELETE FROM tactic_suites WHERE owner_player_id = ?", id);
  }

  @Override
  public boolean moveSuiteToIndex(PlayerId ownerId, TacticSuiteId suiteId, int targetIndex) {
    assertAvailable();
    List<String> ordered = jdbcTemplate.query(
        "SELECT id FROM tactic_suites WHERE owner_player_id = ? ORDER BY display_order",
        (resultSet, rowNum) -> resultSet.getString(1), ownerId.value());
    int currentIndex = ordered.indexOf(suiteId.value().toString());
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= ordered.size()) return false;
    ordered.remove(currentIndex);
    ordered.add(targetIndex, suiteId.value().toString());
    for (int index = 0; index < ordered.size(); index++) {
      jdbcTemplate.update("UPDATE tactic_suites SET display_order = ? WHERE id = ? AND owner_player_id = ?", index, ordered.get(index), ownerId.value());
    }
    return true;
  }

  private void insertExercise(String suiteId, TacticExercise exercise, int displayOrder) {
    String exerciseId = exercise.id().value().toString();
    jdbcTemplate.update(
        "INSERT INTO tactic_exercises (id, suite_id, title, initial_fen, display_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        exerciseId, suiteId, exercise.title(), fenService.fromSnapshot(exercise.solution().initialPosition()).getValue(),
        displayOrder, exercise.createdAt().toEpochMilli(), exercise.updatedAt().toEpochMilli());
    List<AnalysisNode> roots = exercise.solution().tree().roots();
    for (int index = 0; index < roots.size(); index++) insertNode(exerciseId, exercise.solution().tree(), roots.get(index), null, index);
  }

  private void insertNode(String exerciseId, AnalysisTree tree, AnalysisNode node, AnalysisNodeId parentId, int displayOrder) {
    Ply ply = node.ply();
    MoveDescriptor move = ply.move();
    jdbcTemplate.update(
        "INSERT INTO tactic_solution_nodes (id, exercise_id, parent_node_id, ply_id, move_from, move_to, promotion_piece, san, capture, castle, en_passant, moving_color, move_number, resulting_fen, display_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        node.id().value().toString(), exerciseId, parentId == null ? null : parentId.value().toString(), ply.id().toString(),
        move.from().toAlgebraic(), move.to().toAlgebraic(), move.promotion().map(Enum::name).orElse(null), move.san().getValue(),
        bool(move.capture()), bool(move.castling()), bool(move.enPassant()), ply.movingColor().name(), ply.moveNumber(),
        fenService.fromSnapshot(ply.resultingPosition()).getValue(), displayOrder);
    List<AnalysisNode> children = tree.children(node.id());
    for (int index = 0; index < children.size(); index++) insertNode(exerciseId, tree, children.get(index), node.id(), index);
  }

  private TacticSuite hydrateExercises(TacticSuite suite) {
    List<TacticExercise> exercises = jdbcTemplate.query(
        "SELECT id, title, initial_fen, created_at, updated_at FROM tactic_exercises WHERE suite_id = ? ORDER BY display_order",
        (resultSet, rowNum) -> restoreExercise(resultSet.getString("id"), resultSet.getString("title"), resultSet.getString("initial_fen"), resultSet.getLong("created_at"), resultSet.getLong("updated_at")),
        suite.id().value().toString());
    return TacticSuite.restore(suite.id(), suite.ownerId(), suite.title(), suite.description(), exercises, suite.createdAt(), suite.updatedAt());
  }

  private TacticExercise restoreExercise(String id, String title, String initialFen, long createdAt, long updatedAt) {
    AnalysisTree tree = readTree(id);
    PositionSnapshot initialPosition = rulesEngine.positionFrom(Fen.of(initialFen));
    return new TacticExercise(new TacticExerciseId(UUID.fromString(id)), title,
        new AnalysisContent(initialPosition, Optional.empty(), tree), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt));
  }

  private TacticExercise restoreExerciseRow(String exerciseId) {
    return jdbcTemplate.query(
            "SELECT id, title, initial_fen, created_at, updated_at FROM tactic_exercises WHERE id = ?",
            (resultSet, rowNum) -> restoreExercise(
                resultSet.getString("id"), resultSet.getString("title"),
                resultSet.getString("initial_fen"), resultSet.getLong("created_at"),
                resultSet.getLong("updated_at")),
            exerciseId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown tactic exercise " + exerciseId));
  }

  private AnalysisTree readTree(String exerciseId) {
    List<NodeRow> rows = jdbcTemplate.query(
        "SELECT id, parent_node_id, ply_id, move_from, move_to, promotion_piece, san, capture, castle, en_passant, moving_color, move_number, resulting_fen, display_order FROM tactic_solution_nodes WHERE exercise_id = ? ORDER BY display_order",
        (resultSet, rowNum) -> new NodeRow(resultSet.getString("id"), resultSet.getString("parent_node_id"), resultSet.getString("ply_id"), resultSet.getString("move_from"), resultSet.getString("move_to"), resultSet.getString("promotion_piece"), resultSet.getString("san"), resultSet.getInt("capture") != 0, resultSet.getInt("castle") != 0, resultSet.getInt("en_passant") != 0, resultSet.getString("moving_color"), resultSet.getInt("move_number"), resultSet.getString("resulting_fen"), resultSet.getInt("display_order")),
        exerciseId);
    Map<String, List<NodeRow>> children = new LinkedHashMap<>();
    List<NodeRow> roots = new ArrayList<>();
    for (NodeRow row : rows) {
      if (row.parentId == null) roots.add(row); else children.computeIfAbsent(row.parentId, ignored -> new ArrayList<>()).add(row);
    }
    AnalysisTree tree = new AnalysisTree();
    for (NodeRow root : roots) restoreNode(tree, root, null, children);
    return tree;
  }

  private void restoreNode(AnalysisTree tree, NodeRow row, AnalysisNodeId parentId, Map<String, List<NodeRow>> children) {
    AnalysisNodeId nodeId = new AnalysisNodeId(UUID.fromString(row.id));
    AnalysisNode node = parentId == null ? tree.addRoot(toPly(row), nodeId) : tree.addChild(parentId, toPly(row), nodeId);
    for (NodeRow child : children.getOrDefault(row.id, List.of())) restoreNode(tree, child, node.id(), children);
  }

  private Ply toPly(NodeRow row) {
    MoveDescriptor move = new MoveDescriptor(Square.of(row.from), Square.of(row.to), SanMove.of(row.san), row.capture, row.castle, row.enPassant,
        row.promotion == null ? Optional.empty() : Optional.of(PieceType.valueOf(row.promotion)));
    PositionSnapshot base = rulesEngine.positionFrom(Fen.of(row.resultingFen));
    PositionSnapshot resulting = new PositionSnapshot(base.pieces(), base.activeColor(), base.castlingRights(), base.enPassantTarget(), base.halfmoveClock(), base.fullmoveNumber(), Optional.of(move), base.check(), base.mate(), base.stalemate());
    return new Ply(UUID.fromString(row.plyId), move, resulting, row.moveNumber, PieceColor.valueOf(row.movingColor));
  }

  private TacticSuite suiteRow(String id, long ownerId, String title, String description, long createdAt, long updatedAt) {
    return TacticSuite.restore(new TacticSuiteId(UUID.fromString(id)), PlayerId.of(ownerId), title, Optional.ofNullable(description), List.of(), Instant.ofEpochMilli(createdAt), Instant.ofEpochMilli(updatedAt));
  }

  private int nextSuiteOrder(PlayerId ownerId) {
    Integer max = jdbcTemplate.queryForObject("SELECT MAX(display_order) FROM tactic_suites WHERE owner_player_id = ?", Integer.class, ownerId.value());
    return (max == null ? -1 : max) + 1;
  }

  private int bool(boolean value) { return value ? 1 : 0; }

  private void assertAvailable() {
    if (!availability.isAvailable()) throw new PersistenceUnavailableException("Tactic persistence is unavailable" + availability.reason().map(reason -> ": " + reason).orElse(""));
  }

  private record NodeRow(String id, String parentId, String plyId, String from, String to, String promotion, String san, boolean capture, boolean castle, boolean enPassant, String movingColor, int moveNumber, String resultingFen, int displayOrder) {}
  private record SuiteRow(String id, long ownerId, String title, String description, long createdAt, long updatedAt) {}
}
