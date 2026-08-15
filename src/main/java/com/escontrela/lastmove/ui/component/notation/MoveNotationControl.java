package com.escontrela.lastmove.ui.component.notation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * Reusable selectable notation tree that renders both colors in parallel and indents variations.
 *
 * <p>The control owns only rendering and user interaction. The caller supplies the complete tree,
 * identifies the current node and handles {@link PlySelectedEvent}; navigation and chess state
 * remain in the owning workflow.
 */
public final class MoveNotationControl extends Control {

  private final ObservableList<MoveNotationNode> roots = FXCollections.observableArrayList();
  private final ObjectProperty<UUID> selectedNodeId =
      new SimpleObjectProperty<>(this, "selectedNodeId");
  private final ObjectProperty<EventHandler<PlySelectedEvent>> onPlySelected =
      new SimpleObjectProperty<>(this, "onPlySelected");

  public MoveNotationControl() {
    getStyleClass().add("move-notation");
    setMinSize(200, 120);
    setPrefSize(320, 420);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new MoveNotationSkin(this);
  }

  /** Replaces the complete notation tree and validates that every visible node is unique. */
  public void setTree(List<MoveNotationNode> values) {
    List<MoveNotationNode> required =
        List.copyOf(Objects.requireNonNull(values, "values must not be null"));
    Set<UUID> identifiers = new HashSet<>();
    required.forEach(node -> collectIdentifiers(node, identifiers));
    roots.setAll(required);
    if (getSelectedNodeId() != null && !identifiers.contains(getSelectedNodeId())) {
      setSelectedNodeId(null);
    }
  }

  /** Returns an immutable copy of the currently rendered roots. */
  public List<MoveNotationNode> getTree() {
    return List.copyOf(roots);
  }

  ObservableList<MoveNotationNode> observableRoots() {
    return roots;
  }

  public UUID getSelectedNodeId() {
    return selectedNodeId.get();
  }

  /** Updates the visual current-ply marker without emitting a selection request. */
  public void setSelectedNodeId(UUID value) {
    if (value != null && !containsNode(roots, value)) {
      throw new IllegalArgumentException("selectedNodeId must identify a visible node or be null");
    }
    selectedNodeId.set(value);
  }

  public ObjectProperty<UUID> selectedNodeIdProperty() {
    return selectedNodeId;
  }

  public EventHandler<PlySelectedEvent> getOnPlySelected() {
    return onPlySelected.get();
  }

  public void setOnPlySelected(EventHandler<PlySelectedEvent> handler) {
    onPlySelected.set(handler);
  }

  public ObjectProperty<EventHandler<PlySelectedEvent>> onPlySelectedProperty() {
    return onPlySelected;
  }

  void requestSelection(MoveNotationEntry entry) {
    MoveNotationEntry required = Objects.requireNonNull(entry, "entry must not be null");
    setSelectedNodeId(required.nodeId());
    EventHandler<PlySelectedEvent> handler = getOnPlySelected();
    if (handler != null) {
      handler.handle(new PlySelectedEvent(this, required));
    }
  }

  private static void collectIdentifiers(MoveNotationNode node, Set<UUID> identifiers) {
    MoveNotationNode required = Objects.requireNonNull(node, "tree must not contain null");
    if (!identifiers.add(required.entry().nodeId())) {
      throw new IllegalArgumentException("tree must not contain duplicate node identifiers");
    }
    required.continuations().forEach(child -> collectIdentifiers(child, identifiers));
  }

  private static boolean containsNode(List<MoveNotationNode> nodes, UUID identifier) {
    for (MoveNotationNode node : nodes) {
      if (node.entry().nodeId().equals(identifier)
          || containsNode(node.continuations(), identifier)) {
        return true;
      }
    }
    return false;
  }

  /** JavaFX event emitted when the user activates one SAN cell. */
  public static final class PlySelectedEvent extends Event {

    public static final EventType<PlySelectedEvent> PLY_SELECTED =
        new EventType<>(Event.ANY, "PLY_SELECTED");

    private final MoveNotationEntry entry;

    private PlySelectedEvent(MoveNotationControl source, MoveNotationEntry entry) {
      super(source, NULL_SOURCE_TARGET, PLY_SELECTED);
      this.entry = entry;
    }

    public MoveNotationEntry getEntry() {
      return entry;
    }
  }
}
