package com.escontrela.lastmove.ui.component.session;

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
 * Reusable JavaFX selector for a collection of sessions with one clearly marked active item.
 *
 * <p>The control owns only rendering and input. It emits activation and context-menu request
 * events without knowing session identifiers, repositories or rename workflows.
 */
public final class SessionSelectorControl extends Control {

  private final ObservableList<SessionSelectorEntry> entries = FXCollections.observableArrayList();
  private final IntegerProperty selectedSessionIndex =
      new SimpleIntegerProperty(this, "selectedSessionIndex", -1);
  private final ObjectProperty<EventHandler<SessionSelectedEvent>> onSessionSelected =
      new SimpleObjectProperty<>(this, "onSessionSelected");
  private final ObjectProperty<EventHandler<SessionContextRequestedEvent>> onContextRequested =
      new SimpleObjectProperty<>(this, "onContextRequested");

  public SessionSelectorControl() {
    getStyleClass().add("session-selector");
    setMinSize(180, 120);
    setPrefSize(240, 360);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    return new SessionSelectorSkin(this);
  }

  /** Replaces the complete visible session list. */
  public void setEntries(List<SessionSelectorEntry> values) {
    List<SessionSelectorEntry> required =
        List.copyOf(Objects.requireNonNull(values, "values must not be null"));
    for (int index = 0; index < required.size(); index++) {
      if (required.get(index).sessionIndex() != index) {
        throw new IllegalArgumentException("entries must use contiguous list-local indexes");
      }
    }
    entries.setAll(required);
    if (getSelectedSessionIndex() >= entries.size()) {
      setSelectedSessionIndex(-1);
    }
  }

  public List<SessionSelectorEntry> getEntries() {
    return List.copyOf(entries);
  }

  ObservableList<SessionSelectorEntry> observableEntries() {
    return entries;
  }

  public int getSelectedSessionIndex() {
    return selectedSessionIndex.get();
  }

  /** Updates the active marker without emitting an activation request. */
  public void setSelectedSessionIndex(int value) {
    if (value < -1 || value >= entries.size()) {
      throw new IllegalArgumentException(
          "selectedSessionIndex must identify a visible session or be -1");
    }
    selectedSessionIndex.set(value);
  }

  public IntegerProperty selectedSessionIndexProperty() {
    return selectedSessionIndex;
  }

  public EventHandler<SessionSelectedEvent> getOnSessionSelected() {
    return onSessionSelected.get();
  }

  public void setOnSessionSelected(EventHandler<SessionSelectedEvent> handler) {
    onSessionSelected.set(handler);
  }

  public ObjectProperty<EventHandler<SessionSelectedEvent>> onSessionSelectedProperty() {
    return onSessionSelected;
  }

  public EventHandler<SessionContextRequestedEvent> getOnContextRequested() {
    return onContextRequested.get();
  }

  public void setOnContextRequested(EventHandler<SessionContextRequestedEvent> handler) {
    onContextRequested.set(handler);
  }

  public ObjectProperty<EventHandler<SessionContextRequestedEvent>> onContextRequestedProperty() {
    return onContextRequested;
  }

  void requestSelection(SessionSelectorEntry entry) {
    SessionSelectorEntry required = Objects.requireNonNull(entry, "entry must not be null");
    setSelectedSessionIndex(required.sessionIndex());
    EventHandler<SessionSelectedEvent> handler = getOnSessionSelected();
    if (handler != null) {
      handler.handle(new SessionSelectedEvent(this, required));
    }
  }

  void requestContext(SessionSelectorEntry entry, double sceneX, double sceneY) {
    SessionSelectorEntry required = Objects.requireNonNull(entry, "entry must not be null");
    requestSelection(required);
    EventHandler<SessionContextRequestedEvent> handler = getOnContextRequested();
    if (handler != null) {
      handler.handle(new SessionContextRequestedEvent(this, required, sceneX, sceneY));
    }
  }

  /** Event emitted when a session row is activated. */
  public static final class SessionSelectedEvent extends Event {

    public static final EventType<SessionSelectedEvent> SESSION_SELECTED =
        new EventType<>(Event.ANY, "SESSION_SELECTED");

    private final SessionSelectorEntry entry;

    private SessionSelectedEvent(SessionSelectorControl source, SessionSelectorEntry entry) {
      super(source, NULL_SOURCE_TARGET, SESSION_SELECTED);
      this.entry = entry;
    }

    public SessionSelectorEntry getEntry() {
      return entry;
    }
  }

  /** Event emitted when contextual actions are requested for one session row. */
  public static final class SessionContextRequestedEvent extends Event {

    public static final EventType<SessionContextRequestedEvent> CONTEXT_REQUESTED =
        new EventType<>(Event.ANY, "SESSION_CONTEXT_REQUESTED");

    private final SessionSelectorEntry entry;
    private final double sceneX;
    private final double sceneY;

    private SessionContextRequestedEvent(
        SessionSelectorControl source,
        SessionSelectorEntry entry,
        double sceneX,
        double sceneY) {
      super(source, NULL_SOURCE_TARGET, CONTEXT_REQUESTED);
      this.entry = entry;
      this.sceneX = sceneX;
      this.sceneY = sceneY;
    }

    public SessionSelectorEntry getEntry() {
      return entry;
    }

    public double getSceneX() {
      return sceneX;
    }

    public double getSceneY() {
      return sceneY;
    }
  }
}
