package com.escontrela.lastmove.ui.component.tree;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

/**
 * Full-screen explorer for a real analysis tree.
 *
 * <p>The control renders only UI data: owning controllers retain chess navigation and comments
 * persistence, and receive {@link NodeSelectedEvent} when the author activates a SAN node.
 */
public final class MoveTreeOverlay extends StackPane {
  private static final double NODE_WIDTH = 176;
  private static final double NODE_HEIGHT = 58;
  private static final double HORIZONTAL_GAP = 38;
  private static final double VERTICAL_GAP = 88;
  private static final double PADDING = 72;
  private static final double MARKER_CENTER_X = 24;
  private static final double MARKER_CENTER_Y = NODE_HEIGHT / 2;

  private final Pane treeCanvas = new Pane();
  private final ChessBoardControl previewBoard = new ChessBoardControl();
  private final VBox inspector = new VBox(14);
  private final Label commentTitle = new Label();
  private final Label commentBody = new Label();
  private final ObjectProperty<EventHandler<NodeSelectedEvent>> onNodeSelected =
      new SimpleObjectProperty<>(this, "onNodeSelected");
  private final ObjectProperty<EventHandler<NodeSelectedEvent>> onNodeConfirmed =
      new SimpleObjectProperty<>(this, "onNodeConfirmed");
  private List<TreeNode> roots = List.of();
  private TreeNode selectedNode;
  private BorderPane card;
  private double dragStartSceneX;
  private double dragStartSceneY;
  private double dragStartTranslateX;
  private double dragStartTranslateY;
  private double resizeStartSceneX;
  private double resizeStartSceneY;
  private double resizeStartWidth;
  private double resizeStartHeight;

  public MoveTreeOverlay() {
    getStyleClass().add("move-tree-overlay");
    setAlignment(Pos.CENTER);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Label title = new Label("Move tree");
    title.getStyleClass().add("move-tree-title");
    Label support = new Label("Explore the complete main line and its variations.");
    support.getStyleClass().add("move-tree-support");
    VBox titleBox = new VBox(3, title, support);
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    Button close = new Button("×");
    close.getStyleClass().add("move-tree-close");
    close.setAccessibleText("Close move tree");
    close.setOnAction(event -> hide());
    HBox header = new HBox(14, titleBox, spacer, close);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("move-tree-header");

    treeCanvas.getStyleClass().add("move-tree-canvas");
    ScrollPane treeScroll = new ScrollPane(treeCanvas);
    treeScroll.setFitToHeight(true);
    treeScroll.setFitToWidth(true);
    treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    treeScroll.getStyleClass().add("move-tree-scroll");

    VBox commentSection = new VBox(8);
    commentSection.getStyleClass().add("move-tree-comment-viewer");
    commentTitle.getStyleClass().add("move-tree-comment-title");
    commentBody.setWrapText(true);
    commentBody.setMaxWidth(Double.MAX_VALUE);
    commentBody.getStyleClass().add("move-tree-comment-body");
    ScrollPane commentScroll = new ScrollPane(commentBody);
    commentScroll.setFitToWidth(true);
    commentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    commentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    commentScroll.getStyleClass().add("move-tree-comment-scroll");
    VBox.setVgrow(commentScroll, Priority.ALWAYS);
    commentSection.getChildren().addAll(commentTitle, commentScroll);
    commentSection.setMinHeight(160);
    commentSection.setPrefHeight(200);

    Label boardLabel = new Label("Position preview");
    boardLabel.getStyleClass().add("move-tree-preview-heading");
    previewBoard.setMouseTransparent(true);
    previewBoard.setFocusTraversable(false);
    previewBoard.setMinSize(250, 250);
    previewBoard.setPrefSize(280, 280);
    previewBoard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    VBox.setVgrow(previewBoard, Priority.ALWAYS);
    Button openOnBoard = new Button("Open on board");
    openOnBoard.getStyleClass().add("primary-button");
    openOnBoard.setMaxWidth(Double.MAX_VALUE);
    openOnBoard.setOnAction(event -> confirmSelectedNode());
    inspector.getStyleClass().add("move-tree-inspector");
    inspector.setMinWidth(290);
    inspector.setPrefWidth(320);
    inspector.getChildren().addAll(commentSection, boardLabel, previewBoard, openOnBoard);

    HBox explorer = new HBox(treeScroll, inspector);
    explorer.getStyleClass().add("move-tree-explorer");
    HBox.setHgrow(treeScroll, Priority.ALWAYS);
    treeScroll.setMaxWidth(Double.MAX_VALUE);
    Label resizeHandle = new Label("◢");
    resizeHandle.getStyleClass().add("move-tree-resize-handle");
    resizeHandle.setAccessibleText("Resize move tree");
    HBox resizeBar = new HBox(resizeHandle);
    resizeBar.setAlignment(Pos.CENTER_RIGHT);
    this.card = new BorderPane(explorer);
    card.setTop(header);
    card.setBottom(resizeBar);
    card.getStyleClass().add("move-tree-card");
    card.setMinSize(760, 520);
    card.setPrefSize(1120, 700);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    StackPane.setMargin(card, new Insets(28));
    getChildren().add(card);
    configureDrag(header);
    configureResize(resizeHandle);
    hide();
  }

  /** Replaces the full rendered tree. */
  public void setTree(List<TreeNode> roots) {
    this.roots = List.copyOf(Objects.requireNonNull(roots, "roots must not be null"));
    selectedNode = findCurrentNode(this.roots).orElseGet(() -> this.roots.isEmpty() ? null : this.roots.getFirst());
    renderTree();
    if (selectedNode != null) showPreview(selectedNode);
  }

  public EventHandler<NodeSelectedEvent> getOnNodeSelected() {
    return onNodeSelected.get();
  }

  public void setOnNodeSelected(EventHandler<NodeSelectedEvent> handler) {
    onNodeSelected.set(handler);
  }

  public ObjectProperty<EventHandler<NodeSelectedEvent>> onNodeSelectedProperty() {
    return onNodeSelected;
  }

  public EventHandler<NodeSelectedEvent> getOnNodeConfirmed() {
    return onNodeConfirmed.get();
  }

  public void setOnNodeConfirmed(EventHandler<NodeSelectedEvent> handler) {
    onNodeConfirmed.set(handler);
  }

  public ObjectProperty<EventHandler<NodeSelectedEvent>> onNodeConfirmedProperty() {
    return onNodeConfirmed;
  }

  public void show() {
    setManaged(true);
    setVisible(true);
    toFront();
  }

  public void hide() {
    setManaged(false);
    setVisible(false);
  }

  private void renderTree() {
    treeCanvas.getChildren().clear();
    if (roots.isEmpty()) {
      Label empty = new Label("There are no moves to visualize yet.");
      empty.getStyleClass().add("move-tree-empty");
      empty.relocate(PADDING, PADDING);
      treeCanvas.setMinSize(520, 320);
      treeCanvas.getChildren().add(empty);
      return;
    }
    Map<TreeNode, Point2D> locations = new IdentityHashMap<>();
    int leafCount = assignLocations(roots, 0, new LeafCounter(), locations);
    double width = Math.max(720, PADDING * 2 + leafCount * NODE_WIDTH + (leafCount - 1) * HORIZONTAL_GAP);
    int depth = maxDepth(roots);
    double height = Math.max(440, PADDING * 2 + depth * NODE_HEIGHT + Math.max(0, depth - 1) * VERTICAL_GAP);
    treeCanvas.setMinSize(width, height);
    drawConnections(roots, locations);
    drawNodes(roots, locations);
  }

  private int assignLocations(
      List<TreeNode> nodes, int depth, LeafCounter leaves, Map<TreeNode, Point2D> locations) {
    for (TreeNode node : nodes) {
      double x;
      if (node.continuations().isEmpty()) {
        x = PADDING + leaves.next() * (NODE_WIDTH + HORIZONTAL_GAP);
      } else {
        int before = leaves.value();
        assignLocations(node.continuations(), depth + 1, leaves, locations);
        int after = leaves.value() - 1;
        x = PADDING + ((before + after) / 2.0) * (NODE_WIDTH + HORIZONTAL_GAP);
      }
      locations.put(node, new Point2D(x, PADDING + depth * (NODE_HEIGHT + VERTICAL_GAP)));
    }
    return leaves.value();
  }

  private static int maxDepth(List<TreeNode> nodes) {
    int depth = 1;
    for (TreeNode node : nodes) {
      if (!node.continuations().isEmpty()) {
        depth = Math.max(depth, 1 + maxDepth(node.continuations()));
      }
    }
    return depth;
  }

  private void drawConnections(List<TreeNode> nodes, Map<TreeNode, Point2D> locations) {
    for (TreeNode node : nodes) {
      Point2D parent = locations.get(node);
      for (TreeNode child : node.continuations()) {
        Point2D next = locations.get(child);
        Line line =
            new Line(
                parent.getX() + MARKER_CENTER_X,
                parent.getY() + MARKER_CENTER_Y,
                next.getX() + MARKER_CENTER_X,
                next.getY() + MARKER_CENTER_Y);
        line.getStyleClass().add(child.mainLine() ? "move-tree-connector-main" : "move-tree-connector-variation");
        treeCanvas.getChildren().add(line);
      }
      drawConnections(node.continuations(), locations);
    }
  }

  private void drawNodes(List<TreeNode> nodes, Map<TreeNode, Point2D> locations) {
    for (TreeNode node : nodes) {
      Point2D location = locations.get(node);
      StackPane nodeCard = nodeCard(node);
      nodeCard.relocate(location.getX(), location.getY());
      treeCanvas.getChildren().add(nodeCard);
      drawNodes(node.continuations(), locations);
    }
  }

  private StackPane nodeCard(TreeNode node) {
    Circle outerCircle = new Circle(20);
    outerCircle.getStyleClass().add("move-tree-marker-outer");
    Circle innerCircle = new Circle(12);
    innerCircle.getStyleClass().add("move-tree-marker-inner");
    StackPane marker = new StackPane(outerCircle, innerCircle);
    marker.getStyleClass().add("move-tree-marker");
    marker.getStyleClass().add(
        node.mainLine() ? "move-tree-marker-main" : "move-tree-marker-variation");
    if (node.current() || node == selectedNode) marker.getStyleClass().add("move-tree-marker-current");
    marker.setMinSize(48, 48);
    marker.setPrefSize(48, 48);
    marker.setMaxSize(48, 48);

    Button san = new Button(node.moveReference());
    san.getStyleClass().add("move-tree-san");
    san.setAccessibleText("Go to " + node.moveReference());
    san.setOnAction(event -> requestSelection(node));
    HBox body = new HBox(7, marker, san);
    body.setAlignment(Pos.CENTER_LEFT);
    if (!node.comment().isBlank()) {
      ToolbarIconButton comment = new ToolbarIconButton();
      comment.setMinSize(30, 30);
      comment.setPrefSize(30, 30);
      comment.setMaxSize(30, 30);
      comment.setLightIconResource("/images/comment_35dp_000000.png");
      comment.setDarkIconResource("/images/comment_35dp_FFFFFF.png");
      comment.setTooltipText("Show comment");
      comment.setAccessibleText("Show comment for " + node.moveReference());
      comment.getStyleClass().add("move-tree-comment-button");
      comment.setOnAction(event -> selectNode(node));
      body.getChildren().add(comment);
    }
    StackPane card = new StackPane(body);
    card.getStyleClass().add("move-tree-node");
    card.setMinSize(NODE_WIDTH, NODE_HEIGHT);
    card.setPrefSize(NODE_WIDTH, NODE_HEIGHT);
    card.setMaxSize(NODE_WIDTH, NODE_HEIGHT);
    return card;
  }

  private void selectNode(TreeNode node) {
    selectedNode = node;
    showPreview(node);
    renderTree();
    EventHandler<NodeSelectedEvent> handler = getOnNodeSelected();
    if (handler != null) handler.handle(new NodeSelectedEvent(this, node));
  }

  private void showPreview(TreeNode node) {
    commentTitle.setText(node.comment().isBlank() ? "No comment · " + node.moveReference() : node.moveReference());
    commentBody.setText(
        node.comment().isBlank() ? "This move has no saved comment." : node.comment());
    previewBoard.renderPosition(node.position());
  }

  private void requestSelection(TreeNode node) {
    selectNode(node);
  }

  private void confirmSelectedNode() {
    if (selectedNode == null) return;
    EventHandler<NodeSelectedEvent> handler = getOnNodeConfirmed();
    if (handler != null) handler.handle(new NodeSelectedEvent(this, selectedNode));
  }

  private void configureDrag(HBox header) {
    header.setOnMousePressed(
        event -> {
          dragStartSceneX = event.getSceneX();
          dragStartSceneY = event.getSceneY();
          dragStartTranslateX = card.getTranslateX();
          dragStartTranslateY = card.getTranslateY();
        });
    header.setOnMouseDragged(
        event -> {
          card.setTranslateX(dragStartTranslateX + event.getSceneX() - dragStartSceneX);
          card.setTranslateY(dragStartTranslateY + event.getSceneY() - dragStartSceneY);
        });
  }

  private void configureResize(Label handle) {
    handle.setOnMousePressed(
        event -> {
          resizeStartSceneX = event.getSceneX();
          resizeStartSceneY = event.getSceneY();
          resizeStartWidth = card.getWidth();
          resizeStartHeight = card.getHeight();
        });
    handle.setOnMouseDragged(
        event -> {
          card.setPrefWidth(Math.max(card.getMinWidth(), resizeStartWidth + event.getSceneX() - resizeStartSceneX));
          card.setPrefHeight(Math.max(card.getMinHeight(), resizeStartHeight + event.getSceneY() - resizeStartSceneY));
        });
  }

  private static java.util.Optional<TreeNode> findCurrentNode(List<TreeNode> nodes) {
    for (TreeNode node : nodes) {
      if (node.current()) return java.util.Optional.of(node);
      java.util.Optional<TreeNode> child = findCurrentNode(node.continuations());
      if (child.isPresent()) return child;
    }
    return java.util.Optional.empty();
  }

  private static final class LeafCounter {
    private int value;
    int next() { return value++; }
    int value() { return value; }
  }

  public record TreeNode(
      UUID nodeId,
      String moveReference,
      boolean mainLine,
      boolean current,
      String comment,
      PositionSnapshot position,
      List<TreeNode> continuations) {
    public TreeNode {
      Objects.requireNonNull(nodeId, "nodeId must not be null");
      moveReference = Objects.requireNonNullElse(moveReference, "Move");
      comment = Objects.requireNonNullElse(comment, "");
      Objects.requireNonNull(position, "position must not be null");
      continuations = List.copyOf(Objects.requireNonNull(continuations, "continuations must not be null"));
    }
  }

  public static final class NodeSelectedEvent extends Event {
    public static final EventType<NodeSelectedEvent> NODE_SELECTED =
        new EventType<>(Event.ANY, "MOVE_TREE_NODE_SELECTED");
    private final TreeNode node;

    private NodeSelectedEvent(MoveTreeOverlay source, TreeNode node) {
      super(source, NULL_SOURCE_TARGET, NODE_SELECTED);
      this.node = node;
    }

    public TreeNode getNode() { return node; }
  }
}
