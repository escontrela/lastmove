package com.escontrela.lastmove.infrastructure.persistence;

import com.escontrela.lastmove.domain.analysis.AnalysisContent;
import com.escontrela.lastmove.domain.analysis.AnalysisDocument;
import com.escontrela.lastmove.domain.analysis.AnalysisNode;
import com.escontrela.lastmove.domain.analysis.AnalysisNodeId;
import com.escontrela.lastmove.domain.analysis.AnalysisOrigin;
import com.escontrela.lastmove.domain.analysis.AnalysisTree;
import com.escontrela.lastmove.domain.analysis.ChapterNavigation;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.ChessRulesEngine;
import com.escontrela.lastmove.domain.game.GameResult;
import com.escontrela.lastmove.domain.game.MoveDescriptor;
import com.escontrela.lastmove.domain.game.Ply;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.domain.notation.Fen;
import com.escontrela.lastmove.domain.notation.SanMove;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.domain.service.FenService;
import com.escontrela.lastmove.domain.study.Study;
import com.escontrela.lastmove.domain.study.StudyChapter;
import com.escontrela.lastmove.domain.study.StudyChapterId;
import com.escontrela.lastmove.domain.study.StudyId;
import com.escontrela.lastmove.domain.study.StudyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** SQLite-backed implementation of {@link StudyRepository}. */
@Repository
public class SqliteStudyRepository implements StudyRepository {

  private static final String SELECT_STUDIES_SQL =
      "SELECT id, owner_player_id, title, description, display_order, created_at, updated_at FROM studies";
  private static final String DELETE_STUDY_CHILDREN_SQL =
      "DELETE FROM study_chapter_selected_continuations WHERE chapter_id IN (SELECT id FROM study_chapters WHERE study_id = ?)";
  private static final String DELETE_STUDY_NAVIGATION_SQL =
      "DELETE FROM study_chapter_navigation WHERE chapter_id IN (SELECT id FROM study_chapters WHERE study_id = ?)";
  private static final String DELETE_STUDY_NODES_SQL =
      "DELETE FROM study_chapter_nodes WHERE chapter_id IN (SELECT id FROM study_chapters WHERE study_id = ?)";
  private static final String DELETE_STUDY_CHAPTERS_SQL =
      "DELETE FROM study_chapters WHERE study_id = ?";
  private static final String DELETE_STUDY_SQL = "DELETE FROM studies WHERE id = ? AND owner_player_id = ?";
  private static final String DELETE_OWNER_CHILDREN_SQL =
      "DELETE FROM study_chapter_selected_continuations WHERE chapter_id IN (SELECT c.id FROM study_chapters c JOIN studies s ON s.id = c.study_id WHERE s.owner_player_id = ?)";
  private static final String DELETE_OWNER_NAVIGATION_SQL =
      "DELETE FROM study_chapter_navigation WHERE chapter_id IN (SELECT c.id FROM study_chapters c JOIN studies s ON s.id = c.study_id WHERE s.owner_player_id = ?)";
  private static final String DELETE_OWNER_NODES_SQL =
      "DELETE FROM study_chapter_nodes WHERE chapter_id IN (SELECT c.id FROM study_chapters c JOIN studies s ON s.id = c.study_id WHERE s.owner_player_id = ?)";
  private static final String DELETE_OWNER_CHAPTERS_SQL =
      "DELETE FROM study_chapters WHERE study_id IN (SELECT id FROM studies WHERE owner_player_id = ?)";
  private static final String DELETE_OWNER_STUDIES_SQL = "DELETE FROM studies WHERE owner_player_id = ?";

  private static final String INSERT_CHAPTER_SQL =
      "INSERT INTO study_chapters (id, study_id, title, origin, initial_fen, source_result, display_order, created_at, updated_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String INSERT_NODE_SQL =
      "INSERT INTO study_chapter_nodes (id, chapter_id, parent_node_id, ply_id, move_from, move_to, promotion_piece, san, capture, castle, en_passant, moving_color, move_number, resulting_fen, display_order) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
  private static final String INSERT_NAVIGATION_SQL =
      "INSERT INTO study_chapter_navigation (chapter_id, current_node_id, selected_root_node_id, updated_at) "
          + "VALUES (?, ?, ?, ?) "
          + "ON CONFLICT(chapter_id) DO UPDATE SET current_node_id = excluded.current_node_id, selected_root_node_id = excluded.selected_root_node_id, updated_at = excluded.updated_at";
  private static final String INSERT_SELECTED_CONTINUATION_SQL =
      "INSERT INTO study_chapter_selected_continuations (chapter_id, parent_node_id, selected_child_node_id) "
          + "VALUES (?, ?, ?)";
  private static final String DELETE_SELECTED_CONTINUATIONS_SQL =
      "DELETE FROM study_chapter_selected_continuations WHERE chapter_id = ?";

  private static final String SELECT_CHAPTER_SQL =
      "SELECT id, study_id, title, origin, initial_fen, source_result, display_order, created_at, updated_at "
          + "FROM study_chapters";
  private static final String SELECT_NODES_SQL =
      "SELECT id, chapter_id, parent_node_id, ply_id, move_from, move_to, promotion_piece, san, capture, castle, en_passant, moving_color, move_number, resulting_fen, display_order "
          + "FROM study_chapter_nodes WHERE chapter_id = ?";

  private final JdbcTemplate jdbcTemplate;
  private final PersistenceAvailability availability;
  private final ChessRulesEngine rulesEngine;
  private final FenService fenService;

  public SqliteStudyRepository(
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
  public Study save(Study study) {
    assertAvailable();
    Objects.requireNonNull(study, "study must not be null");
    String studyId = study.id().value().toString();
    Map<String, String> chapterComments = existingChapterComments(studyId);
    Map<String, String> moveComments = existingMoveComments(studyId);
    List<Integer> existingOrders =
        jdbcTemplate.query(
            "SELECT display_order FROM studies WHERE id = ?", (resultSet, rowNum) -> resultSet.getInt(1), studyId);
    int displayOrder = existingOrders.isEmpty() ? nextStudyOrder(study.ownerId()) : existingOrders.get(0);
    if (!existingOrders.isEmpty()) {
      jdbcTemplate.update(
          "UPDATE studies SET title = ?, description = ?, updated_at = ? WHERE id = ?",
          study.title(),
          study.description().orElse(null),
          study.updatedAt().toEpochMilli(),
          studyId);
    } else {
      jdbcTemplate.update(
          "INSERT INTO studies (id, owner_player_id, title, description, display_order, created_at, updated_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?)",
          studyId,
          study.ownerId().value(),
          study.title(),
          study.description().orElse(null),
          displayOrder,
          study.createdAt().toEpochMilli(),
          study.updatedAt().toEpochMilli());
    }

    jdbcTemplate.update(DELETE_STUDY_CHILDREN_SQL, studyId);
    jdbcTemplate.update(DELETE_STUDY_NAVIGATION_SQL, studyId);
    jdbcTemplate.update(DELETE_STUDY_NODES_SQL, studyId);
    jdbcTemplate.update(DELETE_STUDY_CHAPTERS_SQL, studyId);
    List<StudyChapter> chapters = study.chapters();
    for (int index = 0; index < chapters.size(); index++) {
      insertChapter(studyId, chapters.get(index), index);
    }
    restoreComments(chapterComments, moveComments);
    return study;
  }

  private Map<String, String> existingChapterComments(String studyId) {
    Map<String, String> result = new LinkedHashMap<>();
    jdbcTemplate
        .query(
            "SELECT cc.chapter_id, cc.comment FROM study_chapter_comments cc JOIN study_chapters c ON c.id=cc.chapter_id WHERE c.study_id=?",
            (resultSet, rowNumber) ->
                Map.entry(resultSet.getString("chapter_id"), resultSet.getString("comment")),
            studyId)
        .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
    return result;
  }

  private Map<String, String> existingMoveComments(String studyId) {
    Map<String, String> result = new LinkedHashMap<>();
    jdbcTemplate
        .query(
            "SELECT mc.node_id, mc.comment FROM study_move_comments mc JOIN study_chapters c ON c.id=mc.chapter_id WHERE c.study_id=?",
            (resultSet, rowNumber) ->
                Map.entry(resultSet.getString("node_id"), resultSet.getString("comment")),
            studyId)
        .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
    return result;
  }

  private void restoreComments(Map<String, String> chapterComments, Map<String, String> moveComments) {
    long now = Instant.now().toEpochMilli();
    chapterComments.forEach((chapterId, comment) -> {
      if (!jdbcTemplate.queryForList("SELECT id FROM study_chapters WHERE id=?", chapterId).isEmpty()) {
        jdbcTemplate.update("INSERT INTO study_chapter_comments(chapter_id,comment,updated_at) VALUES(?,?,?)", chapterId, comment, now);
      }
    });
    moveComments.forEach((nodeId, comment) -> {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT chapter_id FROM study_chapter_nodes WHERE id=?", nodeId);
      if (!rows.isEmpty()) {
        jdbcTemplate.update("INSERT INTO study_move_comments(chapter_id,node_id,comment,updated_at) VALUES(?,?,?,?)", rows.getFirst().get("chapter_id"), nodeId, comment, now);
      }
    });
  }

  @Override
  public Optional<Study> findByIdAndOwner(StudyId studyId, PlayerId ownerId) {
    assertAvailable();
    return jdbcTemplate
        .query(
            SELECT_STUDIES_SQL + " WHERE id = ? AND owner_player_id = ?",
            studyMapper(),
            studyId.value().toString(),
            ownerId.value())
        .stream()
        .findFirst()
        .map(study -> hydrateChapters(study, studyId));
  }

  @Override
  public List<Study> findAllByOwner(PlayerId ownerId) {
    assertAvailable();
    return jdbcTemplate
        .query(
            SELECT_STUDIES_SQL + " WHERE owner_player_id = ? ORDER BY display_order",
            studyMapper(),
            ownerId.value())
        .stream()
        .map(study -> hydrateChapters(study, study.id()))
        .toList();
  }

  @Override
  public boolean deleteByIdAndOwner(StudyId studyId, PlayerId ownerId) {
    assertAvailable();
    String id = studyId.value().toString();
    jdbcTemplate.update(DELETE_STUDY_CHILDREN_SQL, id);
    jdbcTemplate.update(DELETE_STUDY_NAVIGATION_SQL, id);
    jdbcTemplate.update(DELETE_STUDY_NODES_SQL, id);
    jdbcTemplate.update(DELETE_STUDY_CHAPTERS_SQL, id);
    return jdbcTemplate.update(DELETE_STUDY_SQL, id, ownerId.value()) > 0;
  }

  @Override
  public void deleteByOwner(PlayerId ownerId) {
    assertAvailable();
    Long playerId = ownerId.value();
    jdbcTemplate.update(DELETE_OWNER_CHILDREN_SQL, playerId);
    jdbcTemplate.update(DELETE_OWNER_NAVIGATION_SQL, playerId);
    jdbcTemplate.update(DELETE_OWNER_NODES_SQL, playerId);
    jdbcTemplate.update(DELETE_OWNER_CHAPTERS_SQL, playerId);
    jdbcTemplate.update(DELETE_OWNER_STUDIES_SQL, playerId);
  }

  @Override
  public boolean moveStudyToIndex(PlayerId ownerId, StudyId studyId, int targetIndex) {
    assertAvailable();
    List<String> ordered =
        jdbcTemplate.query(
            "SELECT id FROM studies WHERE owner_player_id = ? ORDER BY display_order",
            (resultSet, rowNum) -> resultSet.getString("id"),
            ownerId.value());
    int currentIndex = ordered.indexOf(studyId.value().toString());
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= ordered.size()) {
      return false;
    }
    if (currentIndex == targetIndex) {
      return true;
    }
    ordered.remove(currentIndex);
    ordered.add(targetIndex, studyId.value().toString());
    for (int index = 0; index < ordered.size(); index++) {
      jdbcTemplate.update(
          "UPDATE studies SET display_order = ? WHERE id = ? AND owner_player_id = ?",
          index,
          ordered.get(index),
          ownerId.value());
    }
    return true;
  }

  @Override
  public void updateChapterNavigation(
      StudyChapterId chapterId, ChapterNavigation navigation, Instant updatedAt) {
    assertAvailable();
    String id = chapterId.value().toString();
    jdbcTemplate.update(
        INSERT_NAVIGATION_SQL,
        id,
        navigation.currentNodeId().map(nodeId -> nodeId.value().toString()).orElse(null),
        navigation.selectedRootNodeId().map(nodeId -> nodeId.value().toString()).orElse(null),
        updatedAt.toEpochMilli());
    jdbcTemplate.update(DELETE_SELECTED_CONTINUATIONS_SQL, id);
    navigation.selectedContinuationIds().forEach(
        (parent, child) ->
            jdbcTemplate.update(
                INSERT_SELECTED_CONTINUATION_SQL,
                id,
                parent.value().toString(),
                child.value().toString()));
  }

  private void insertChapter(String studyId, StudyChapter chapter, int displayOrder) {
    jdbcTemplate.update(
        INSERT_CHAPTER_SQL,
        chapter.id().value().toString(),
        studyId,
        chapter.title(),
        chapter.origin().name(),
        fenService.fromSnapshot(chapter.document().initialPosition()).getValue(),
        chapter.document().sourceResult().map(GameResult::getPgn).orElse(null),
        displayOrder,
        chapter.createdAt().toEpochMilli(),
        chapter.updatedAt().toEpochMilli());
    String chapterId = chapter.id().value().toString();
    AnalysisTree tree = chapter.document().content().tree();
    List<AnalysisNode> roots = tree.roots();
    for (int index = 0; index < roots.size(); index++) {
      insertNode(chapterId, tree, roots.get(index), null, index);
    }
    ChapterNavigation navigation = chapter.document().navigation();
    jdbcTemplate.update(
        INSERT_NAVIGATION_SQL,
        chapterId,
        navigation.currentNodeId().map(nodeId -> nodeId.value().toString()).orElse(null),
        navigation.selectedRootNodeId().map(nodeId -> nodeId.value().toString()).orElse(null),
        navigation.lastVisitedAt().map(Instant::toEpochMilli).orElseGet(() -> chapter.updatedAt().toEpochMilli()));
    navigation.selectedContinuationIds().forEach(
        (parent, child) ->
            jdbcTemplate.update(
                INSERT_SELECTED_CONTINUATION_SQL,
                chapterId,
                parent.value().toString(),
                child.value().toString()));
  }

  private void insertNode(
      String chapterId, AnalysisTree tree, AnalysisNode node, AnalysisNodeId parentId, int displayOrder) {
    Ply ply = node.ply();
    MoveDescriptor move = ply.move();
    jdbcTemplate.update(
        INSERT_NODE_SQL,
        node.id().value().toString(),
        chapterId,
        parentId == null ? null : parentId.value().toString(),
        ply.id().toString(),
        move.from().toAlgebraic(),
        move.to().toAlgebraic(),
        move.promotion().map(Enum::name).orElse(null),
        move.san().getValue(),
        bool(move.capture()),
        bool(move.castling()),
        bool(move.enPassant()),
        ply.movingColor().name(),
        ply.moveNumber(),
        fenService.fromSnapshot(ply.resultingPosition()).getValue(),
        displayOrder);
    List<AnalysisNode> children = tree.children(node.id());
    for (int index = 0; index < children.size(); index++) {
      insertNode(chapterId, tree, children.get(index), node.id(), index);
    }
  }

  private Study hydrateChapters(Study study, StudyId studyId) {
    List<ChapterRow> chapterRows =
        jdbcTemplate.query(
            SELECT_CHAPTER_SQL + " WHERE study_id = ? ORDER BY display_order",
            chapterRowMapper(),
            studyId.value().toString());
    List<StudyChapter> chapters = new ArrayList<>(chapterRows.size());
    for (ChapterRow row : chapterRows) {
      chapters.add(restoreChapter(row));
    }
    return Study.restore(
        study.id(),
        study.ownerId(),
        study.title(),
        study.description(),
        chapters,
        study.createdAt(),
        study.updatedAt());
  }

  private StudyChapter restoreChapter(ChapterRow row) {
    PositionSnapshot initialPosition = rulesEngine.positionFrom(Fen.of(row.initialFen));
    AnalysisTree tree = readTree(row.id);
    ChapterNavigation navigation = readNavigation(row.id, tree);
    Optional<GameResult> sourceResult =
        row.sourceResult == null ? Optional.empty() : Optional.of(GameResult.fromPgn(row.sourceResult));
    return new StudyChapter(
        new StudyChapterId(UUID.fromString(row.id)),
        row.title,
        AnalysisOrigin.valueOf(row.origin),
        new AnalysisDocument(new AnalysisContent(initialPosition, sourceResult, tree), navigation),
        Instant.ofEpochMilli(row.createdAt),
        Instant.ofEpochMilli(row.updatedAt));
  }

  private AnalysisTree readTree(String chapterId) {
    List<NodeRow> rows = jdbcTemplate.query(SELECT_NODES_SQL, nodeRowMapper(), chapterId);
    Map<String, List<NodeRow>> childrenByParent = new LinkedHashMap<>();
    List<NodeRow> roots = new ArrayList<>();
    for (NodeRow row : rows) {
      if (row.parentNodeId == null) {
        roots.add(row);
      } else {
        childrenByParent.computeIfAbsent(row.parentNodeId, ignored -> new ArrayList<>()).add(row);
      }
    }
    for (List<NodeRow> siblings : childrenByParent.values()) {
      siblings.sort((left, right) -> Integer.compare(left.displayOrder, right.displayOrder));
    }
    roots.sort((left, right) -> Integer.compare(left.displayOrder, right.displayOrder));

    AnalysisTree tree = new AnalysisTree();
    for (NodeRow root : roots) {
      insertNodeRow(tree, root, null, childrenByParent);
    }
    return tree;
  }

  private void insertNodeRow(
      AnalysisTree tree, NodeRow row, AnalysisNodeId parentId, Map<String, List<NodeRow>> childrenByParent) {
    Ply ply = toPly(row);
    AnalysisNodeId nodeId = new AnalysisNodeId(UUID.fromString(row.id));
    AnalysisNode node =
        parentId == null ? tree.addRoot(ply, nodeId) : tree.addChild(parentId, ply, nodeId);
    for (NodeRow child : childrenByParent.getOrDefault(row.id, List.of())) {
      insertNodeRow(tree, child, node.id(), childrenByParent);
    }
  }

  private Ply toPly(NodeRow row) {
    Optional<PieceType> promotion =
        row.promotionPiece == null
            ? Optional.empty()
            : Optional.of(PieceType.valueOf(row.promotionPiece));
    MoveDescriptor move =
        new MoveDescriptor(
            Square.of(row.moveFrom),
            Square.of(row.moveTo),
            SanMove.of(row.san),
            row.capture,
            row.castle,
            row.enPassant,
            promotion);
    PositionSnapshot base = rulesEngine.positionFrom(Fen.of(row.resultingFen));
    PositionSnapshot resulting =
        new PositionSnapshot(
            base.pieces(),
            base.activeColor(),
            base.castlingRights(),
            base.enPassantTarget(),
            base.halfmoveClock(),
            base.fullmoveNumber(),
            Optional.of(move),
            base.check(),
            base.mate(),
            base.stalemate());
    return new Ply(
        UUID.fromString(row.plyId),
        move,
        resulting,
        row.moveNumber,
        PieceColor.valueOf(row.movingColor));
  }

  private ChapterNavigation readNavigation(String chapterId, AnalysisTree tree) {
    Optional<NavigationRow> navigation =
        jdbcTemplate
            .query(
                "SELECT chapter_id, current_node_id, selected_root_node_id, updated_at "
                    + "FROM study_chapter_navigation WHERE chapter_id = ?",
                navigationRowMapper(),
                chapterId)
            .stream()
            .findFirst();
    List<SelectedContinuationRow> selected =
        jdbcTemplate.query(
            "SELECT chapter_id, parent_node_id, selected_child_node_id "
                + "FROM study_chapter_selected_continuations WHERE chapter_id = ?",
            selectedRowMapper(),
            chapterId);
    Optional<AnalysisNodeId> currentNode =
        navigation
            .flatMap(NavigationRow::optionalCurrentNodeId)
            .flatMap(value -> tree.find(new AnalysisNodeId(UUID.fromString(value))))
            .map(AnalysisNode::id);
    Optional<AnalysisNodeId> selectedRoot =
        navigation
            .flatMap(NavigationRow::optionalSelectedRootNodeId)
            .flatMap(value -> tree.find(new AnalysisNodeId(UUID.fromString(value))))
            .map(AnalysisNode::id);
    Map<AnalysisNodeId, AnalysisNodeId> continuations = new LinkedHashMap<>();
    for (SelectedContinuationRow row : selected) {
      tree.find(new AnalysisNodeId(UUID.fromString(row.parentNodeId)))
          .ifPresent(
              parent ->
                  tree.find(new AnalysisNodeId(UUID.fromString(row.selectedChildNodeId)))
                      .ifPresent(child -> continuations.put(parent.id(), child.id())));
    }
    Optional<Instant> lastVisited =
        navigation.flatMap(NavigationRow::optionalUpdatedAt).map(Instant::ofEpochMilli);
    return new ChapterNavigation(currentNode, selectedRoot, continuations, lastVisited);
  }

  private int nextStudyOrder(PlayerId ownerId) {
    Integer max =
        jdbcTemplate.queryForObject(
            "SELECT MAX(display_order) FROM studies WHERE owner_player_id = ?",
            Integer.class,
            ownerId.value());
    return (max == null ? -1 : max) + 1;
  }

  private RowMapper<Study> studyMapper() {
    return (resultSet, rowNum) ->
        Study.restore(
            new StudyId(UUID.fromString(resultSet.getString("id"))),
            PlayerId.of(resultSet.getLong("owner_player_id")),
            resultSet.getString("title"),
            Optional.ofNullable(resultSet.getString("description")),
            List.of(),
            Instant.ofEpochMilli(resultSet.getLong("created_at")),
            Instant.ofEpochMilli(resultSet.getLong("updated_at")));
  }

  private RowMapper<ChapterRow> chapterRowMapper() {
    return (resultSet, rowNum) ->
        new ChapterRow(
            resultSet.getString("id"),
            resultSet.getString("study_id"),
            resultSet.getString("title"),
            resultSet.getString("origin"),
            resultSet.getString("initial_fen"),
            resultSet.getString("source_result"),
            resultSet.getInt("display_order"),
            resultSet.getLong("created_at"),
            resultSet.getLong("updated_at"));
  }

  private RowMapper<NodeRow> nodeRowMapper() {
    return (resultSet, rowNum) ->
        new NodeRow(
            resultSet.getString("id"),
            resultSet.getString("chapter_id"),
            resultSet.getString("parent_node_id"),
            resultSet.getString("ply_id"),
            resultSet.getString("move_from"),
            resultSet.getString("move_to"),
            resultSet.getString("promotion_piece"),
            resultSet.getString("san"),
            resultSet.getInt("capture") != 0,
            resultSet.getInt("castle") != 0,
            resultSet.getInt("en_passant") != 0,
            resultSet.getString("moving_color"),
            resultSet.getInt("move_number"),
            resultSet.getString("resulting_fen"),
            resultSet.getInt("display_order"));
  }

  private RowMapper<NavigationRow> navigationRowMapper() {
    return (resultSet, rowNum) ->
        new NavigationRow(
            resultSet.getString("current_node_id"),
            resultSet.getString("selected_root_node_id"),
            resultSet.getLong("updated_at"));
  }

  private RowMapper<SelectedContinuationRow> selectedRowMapper() {
    return (resultSet, rowNum) ->
        new SelectedContinuationRow(
            resultSet.getString("parent_node_id"), resultSet.getString("selected_child_node_id"));
  }

  private int bool(boolean value) {
    return value ? 1 : 0;
  }

  private void assertAvailable() {
    if (!availability.isAvailable()) {
      throw new PersistenceUnavailableException(
          "Study persistence is unavailable"
              + availability.reason().map(reason -> ": " + reason).orElse(""));
    }
  }

  private record ChapterRow(
      String id,
      String studyId,
      String title,
      String origin,
      String initialFen,
      String sourceResult,
      int displayOrder,
      long createdAt,
      long updatedAt) {}

  private record NodeRow(
      String id,
      String chapterId,
      String parentNodeId,
      String plyId,
      String moveFrom,
      String moveTo,
      String promotionPiece,
      String san,
      boolean capture,
      boolean castle,
      boolean enPassant,
      String movingColor,
      int moveNumber,
      String resultingFen,
      int displayOrder) {}

  private record NavigationRow(String currentNodeId, String selectedRootNodeId, long updatedAt) {

    Optional<String> optionalCurrentNodeId() {
      return Optional.ofNullable(currentNodeId);
    }

    Optional<String> optionalSelectedRootNodeId() {
      return Optional.ofNullable(selectedRootNodeId);
    }

    Optional<Long> optionalUpdatedAt() {
      return Optional.of(updatedAt);
    }
  }

  private record SelectedContinuationRow(String parentNodeId, String selectedChildNodeId) {}
}
