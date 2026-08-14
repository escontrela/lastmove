package com.escontrela.lastmove.ui.component.notation;

import java.util.List;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

/**
 * Reusable, selectable chess-notation control that renders White and Black moves in parallel.
 *
 * <p>The control owns presentation and interaction only. Callers provide a sequential list of
 * {@link MoveNotationEntry} values and react to {@link PlySelectedEvent}; navigation and chess
 * state remain in the owning screen or application service.
 */
public final class MoveNotationControl extends Control {

  private final ObservableList<MoveNotationEntry> entries = FXCollections.observableArrayList();
  private final IntegerProperty selectedPlyIndex =
      new SimpleIntegerProperty(this, "selectedPlyIndex", -1);
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

  /** Replaces the complete visible notation line. */
  public void setEntries(List<MoveNotationEntry> values) {
    List<MoveNotationEntry> required =
        List.copyOf(Objects.requireNonNull(values, "values must not be null"));
    for (int index = 0; index < required.size(); index++) {
      if (required.get(index).plyIndex() != index) {
        throw new IllegalArgumentException("entries must use contiguous line-local ply indexes");
      }
    }
    entries.setAll(required);
    if (getSelectedPlyIndex() >= entries.size()) {
      setSelectedPlyIndex(-1);
    }
  }

  /** Returns an immutable copy of the currently rendered line. */
  public List<MoveNotationEntry> getEntries() {
    return List.copyOf(entries);
  }

  ObservableList<MoveNotationEntry> observableEntries() {
    return entries;
  }

  public int getSelectedPlyIndex() {
    return selectedPlyIndex.get();
  }

  /** Updates the visual current-ply marker without emitting a selection request. */
  public void setSelectedPlyIndex(int value) {
    if (value < -1 || value >= entries.size()) {
      throw new IllegalArgumentException("selectedPlyIndex must identify a visible ply or be -1");
    }
    selectedPlyIndex.set(value);
  }

  public IntegerProperty selectedPlyIndexProperty() {
    return selectedPlyIndex;
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
    setSelectedPlyIndex(required.plyIndex());
    EventHandler<PlySelectedEvent> handler = getOnPlySelected();
    if (handler != null) {
      handler.handle(new PlySelectedEvent(this, required));
    }
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
