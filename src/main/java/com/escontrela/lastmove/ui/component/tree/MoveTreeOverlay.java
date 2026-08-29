package com.escontrela.lastmove.ui.component.tree;

import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.component.board.ChessBoardControl;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.transform.Scale;

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
  private static final double MIN_ZOOM = 0.55;
  private static final double MAX_ZOOM = 1.75;
  private static final double ZOOM_STEP = 0.15;

  private final Pane treeCanvas = new Pane();
  private final Scale treeScale = new Scale(1, 1, 0, 0);
  private final AnchorPane zoomCanvas = new AnchorPane(treeCanvas);
  private final ScrollPane treeScroll = new ScrollPane(zoomCanvas);
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
  private Point2D initialNodeCenter;
  private double canvasWidth;
  private double canvasHeight;
  private double zoom = 1.0;
  private double dragStartSceneX;
  private double dragStartSceneY;
  private double dragStartTranslateX;
  private double dragStartTranslateY;
  private double resizeStartSceneX;
  private double resizeStartSceneY;
  private double resizeStartWidth;
  private double resizeStartHeight;
  private boolean panning;
  private double panStartSceneX;
  private double panStartSceneY;
  private double panStartHValue;
  private double panStartVValue;
  private boolean maximized;
  private double preMaximizeWidth;
  private double preMaximizeHeight;

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
    treeCanvas.getStyleClass().add("move-tree-canvas");
    treeCanvas.getTransforms().add(treeScale);
    zoomCanvas.getStyleClass().add("move-tree-zoom-canvas");
    AnchorPane.setTopAnchor(treeCanvas, 0.0);
    AnchorPane.setLeftAnchor(treeCanvas, 0.0);
    treeScroll.setFitToHeight(true);
    treeScroll.setFitToWidth(true);
    treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    treeScroll.getStyleClass().add("move-tree-scroll");

    ToolbarIconButton zoomOut = treeActionButton(
        "/images/zoom_out_35dp_000000.png",
        "/images/zoom_out_35dp_000000.png",
        "Zoom out",
        "Zoom out move tree");
    zoomOut.setOnAction(event -> zoomOut());
    ToolbarIconButton zoomIn = treeActionButton(
        "/images/zoom_in_35dp_000000.png",
        "/images/zoom_in_35dp_FFFFFF.png",
        "Zoom in",
        "Zoom in move tree");
    zoomIn.setOnAction(event -> zoomIn());
    ToolbarIconButton centerInitialNode = treeActionButton(
        "/images/center_focus_weak_35dp_000000.png",
        "/images/center_focus_weak_35dp_FFFFFF.png",
        "Center initial position",
        "Center the initial move tree node");
    centerInitialNode.setOnAction(event -> centerInitialNode());
    ToolbarIconButton maximize = treeActionButton(
        "/images/zoom_out_map_35dp_000000.png",
        "/images/zoom_out_map_35dp_FFFFFF.png",
        "Maximize move tree",
        "Maximize the move tree to the parent window");
    maximize.setOnAction(event -> toggleMaximize());
    HBox treeActions = new HBox(6, zoomOut, zoomIn, centerInitialNode, maximize);
    treeActions.setAlignment(Pos.CENTER_RIGHT);
    treeActions.getStyleClass().add("move-tree-actions");
    HBox header = new HBox(14, titleBox, spacer, treeActions, close);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("move-tree-header");

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
    configurePan();
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
    centerInitialNode();
  }

  public void hide() {
    panning = false;
    zoomCanvas.setCursor(Cursor.DEFAULT);
    if (maximized) restoreCardSize();
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
      updateZoomCanvas(520, 320);
      initialNodeCenter = null;
      treeCanvas.getChildren().add(empty);
      return;
    }
    Map<TreeNode, Point2D> locations = new IdentityHashMap<>();
    int leafCount = assignLocations(roots, 0, new LeafCounter(), locations);
    double width = Math.max(720, PADDING * 2 + leafCount * NODE_WIDTH + (leafCount - 1) * HORIZONTAL_GAP);
    int depth = maxDepth(roots);
    double height = Math.max(440, PADDING * 2 + depth * NODE_HEIGHT + Math.max(0, depth - 1) * VERTICAL_GAP);
    treeCanvas.setMinSize(width, height);
    updateZoomCanvas(width, height);
    Point2D initialLocation = locations.get(roots.getFirst());
    initialNodeCenter = new Point2D(
        initialLocation.getX() + NODE_WIDTH / 2, initialLocation.getY() + NODE_HEIGHT / 2);
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

  private ToolbarIconButton treeActionButton(
      String lightIcon, String darkIcon, String tooltip, String accessibleText) {
    ToolbarIconButton button = new ToolbarIconButton();
    button.getStyleClass().add("move-tree-action-button");
    button.setLightIconResource(lightIcon);
    button.setDarkIconResource(darkIcon);
    button.setTooltipText(tooltip);
    button.setAccessibleText(accessibleText);
    return button;
  }

  private void zoomIn() {
    updateZoom(Math.min(MAX_ZOOM, zoom + ZOOM_STEP));
  }

  private void zoomOut() {
    updateZoom(Math.max(MIN_ZOOM, zoom - ZOOM_STEP));
  }

  private void updateZoom(double zoom) {
    if (Double.compare(this.zoom, zoom) == 0) return;
    this.zoom = zoom;
    treeScale.setX(zoom);
    treeScale.setY(zoom);
    updateZoomCanvas(canvasWidth, canvasHeight);
    centerInitialNode();
  }

  private void updateZoomCanvas(double width, double height) {
    canvasWidth = width;
    canvasHeight = height;
    zoomCanvas.setMinSize(width * zoom, height * zoom);
    zoomCanvas.setPrefSize(width * zoom, height * zoom);
  }

  private void centerInitialNode() {
    if (initialNodeCenter == null) return;
    panning = false;
    javafx.application.Platform.runLater(
        () -> {
          double contentWidth = canvasWidth * zoom;
          double contentHeight = canvasHeight * zoom;
          double centerX = initialNodeCenter.getX() * zoom;
          double centerY = initialNodeCenter.getY() * zoom;
          treeScroll.setHvalue(scrollValue(centerX, treeScroll.getViewportBounds().getWidth(), contentWidth));
          treeScroll.setVvalue(scrollValue(centerY, treeScroll.getViewportBounds().getHeight(), contentHeight));
        });
  }

  private static double scrollValue(double center, double viewport, double content) {
    if (content <= viewport) return 0.0;
    return Math.clamp((center - viewport / 2) / (content - viewport), 0.0, 1.0);
  }

  private void toggleMaximize() {
    if (maximized) {
      restoreCardSize();
      return;
    }
    preMaximizeWidth = card.getPrefWidth();
    preMaximizeHeight = card.getPrefHeight();
    StackPane.setMargin(card, Insets.EMPTY);
    getStyleClass().add("move-tree-overlay-maximized");
    card.setPrefWidth(getWidth());
    card.setPrefHeight(getHeight());
    maximized = true;
  }

  private void restoreCardSize() {
    StackPane.setMargin(card, new Insets(28));
    getStyleClass().remove("move-tree-overlay-maximized");
    card.setPrefWidth(preMaximizeWidth);
    card.setPrefHeight(preMaximizeHeight);
    maximized = false;
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

  private void configurePan() {
    zoomCanvas.setOnMousePressed(
        event -> {
          if (event.getButton() != MouseButton.SECONDARY || hitsNode(event.getTarget())) return;
          panStartSceneX = event.getSceneX();
          panStartSceneY = event.getSceneY();
          panStartHValue = treeScroll.getHvalue();
          panStartVValue = treeScroll.getVvalue();
          panning = true;
          zoomCanvas.setCursor(Cursor.CLOSED_HAND);
          event.consume();
        });
    zoomCanvas.setOnMouseDragged(
        event -> {
          if (!panning) return;
          double contentWidth = canvasWidth * zoom;
          double contentHeight = canvasHeight * zoom;
          double viewportWidth = treeScroll.getViewportBounds().getWidth();
          double viewportHeight = treeScroll.getViewportBounds().getHeight();
          double dx = event.getSceneX() - panStartSceneX;
          double dy = event.getSceneY() - panStartSceneY;
          if (contentWidth > viewportWidth) {
            treeScroll.setHvalue(
                Math.clamp(panStartHValue - dx / (contentWidth - viewportWidth), 0.0, 1.0));
          }
          if (contentHeight > viewportHeight) {
            treeScroll.setVvalue(
                Math.clamp(panStartVValue - dy / (contentHeight - viewportHeight), 0.0, 1.0));
          }
          event.consume();
        });
    zoomCanvas.setOnMouseReleased(
        event -> {
          if (event.getButton() != MouseButton.SECONDARY || !panning) return;
          panning = false;
          zoomCanvas.setCursor(Cursor.DEFAULT);
          event.consume();
        });
    zoomCanvas.setOnMouseMoved(
        event -> {
          if (panning) return;
          zoomCanvas.setCursor(hitsNode(event.getTarget()) ? Cursor.DEFAULT : Cursor.MOVE);
        });
    zoomCanvas.setOnMouseExited(event -> zoomCanvas.setCursor(Cursor.DEFAULT));
  }

  private static boolean hitsNode(Object target) {
    if (!(target instanceof Node node)) return false;
    while (node != null) {
      if (node.getStyleClass().contains("move-tree-node")) return true;
      node = node.getParent();
    }
    return false;
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
