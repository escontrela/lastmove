package com.escontrela.lastmove.ui.component.comment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Read-only, scrollable overview of the annotations that accompany one chapter's PGN. */
public final class SpeakerNotesPanel extends StackPane {
  private final VBox content = new VBox(18);
  private final ToolbarIconButton viewModeButton = new ToolbarIconButton();
  private final Button closeButton = new Button("×");
  private final ObjectProperty<EventHandler<MoveNoteSelectedEvent>> onMoveNoteSelected =
      new SimpleObjectProperty<>(this, "onMoveNoteSelected");
  private String studyComment = "";
  private String chapterTitle = "";
  private String chapterComment = "";
  private List<MoveNote> treeNotes = List.of();
  private List<StoryLine> storyLines = List.of();
  private boolean storyMode = true;
  private double dragStartSceneX;
  private double dragStartSceneY;
  private double dragStartTranslateX;
  private double dragStartTranslateY;
  private double resizeStartSceneX;
  private double resizeStartSceneY;
  private double resizeStartWidth;
  private double resizeStartHeight;

  public SpeakerNotesPanel() {
    getStyleClass().add("speaker-notes-overlay");
    setAlignment(Pos.CENTER_RIGHT);

    Label title = new Label("PGN speaker notes");
    title.getStyleClass().add("speaker-notes-title");
    viewModeButton.getStyleClass().add("speaker-notes-view-mode");
    viewModeButton.setOnAction(event -> toggleViewMode());
    closeButton.getStyleClass().add("speaker-notes-close");
    closeButton.setAccessibleText("Close PGN speaker notes");
    closeButton.setOnAction(event -> hide());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(10, title, spacer, viewModeButton, closeButton);
    header.setAlignment(Pos.CENTER_LEFT);
    header.getStyleClass().add("speaker-notes-header");

    content.getStyleClass().add("speaker-notes-content");
    content.setPadding(new Insets(2, 4, 2, 2));
    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.getStyleClass().add("speaker-notes-scroll");
    VBox.setVgrow(scrollPane, Priority.ALWAYS);

    Label resizeHandle = new Label("◢");
    resizeHandle.getStyleClass().add("speaker-notes-resize-handle");
    resizeHandle.setAccessibleText("Resize speaker notes");
    HBox resizeBar = new HBox(resizeHandle);
    resizeBar.setAlignment(Pos.CENTER_RIGHT);

    VBox card = new VBox(16, header, scrollPane, resizeBar);
    card.getStyleClass().add("speaker-notes-card");
    card.setPadding(new Insets(22));
    card.setMinWidth(320);
    card.setMinHeight(360);
    card.setPrefWidth(420);
    card.setPrefHeight(560);
    card.setMaxWidth(Region.USE_PREF_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    configureDrag(header, card);
    configureResize(resizeHandle, card);
    getChildren().add(card);
    hide();
  }

  public void showNotes(
      String studyComment,
      String chapterTitle,
      String chapterComment,
      List<MoveNote> treeNotes,
      List<StoryLine> storyLines) {
    this.studyComment = Objects.requireNonNullElse(studyComment, "");
    this.chapterTitle = Objects.requireNonNullElse(chapterTitle, "");
    this.chapterComment = Objects.requireNonNullElse(chapterComment, "");
    this.treeNotes = List.copyOf(Objects.requireNonNull(treeNotes, "treeNotes must not be null"));
    this.storyLines = List.copyOf(Objects.requireNonNull(storyLines, "storyLines must not be null"));
    renderNotes();
  }

  private void toggleViewMode() {
    storyMode = !storyMode;
    renderNotes();
  }

  private void renderNotes() {
    viewModeButton.setLightIconResource(
        storyMode ? "/images/graph_2_35dp_000000.png" : "/images/speaker_notes_35dp_000000.png");
    viewModeButton.setDarkIconResource(
        storyMode ? "/images/graph_2_35dp_FFFFFF.png" : "/images/speaker_notes_35dp_FFFFFF.png");
    viewModeButton.setAccessibleText(
        storyMode ? "Show notes as an ASCII tree" : "Show notes as a sequential story");
    viewModeButton.setTooltipText(
        storyMode ? "Show ASCII tree" : "Show sequential story");
    List<javafx.scene.Node> sections = new ArrayList<>();
    addSection(sections, "Study comment", studyComment);
    addSection(sections, "Chapter comment · " + chapterTitle, chapterComment);
    if (storyMode && !storyLines.isEmpty()) {
      sections.add(storySection(storyLines));
    } else if (!storyMode && !treeNotes.isEmpty()) {
      sections.add(movesSection(treeNotes));
    }
    if (sections.isEmpty()) {
      sections.add(message("No comments recorded for this chapter.", "speaker-notes-empty"));
    }
    content.getChildren().setAll(sections);
  }

  private void addSection(List<javafx.scene.Node> sections, String headingText, String value) {
    if (value != null && !value.isBlank()) {
      sections.add(section(headingText, value));
    }
  }

  private VBox movesSection(List<MoveNote> notes) {
    VBox section = new VBox(10);
    section.getStyleClass().add("speaker-notes-section");
    Label heading = new Label("Moves");
    heading.getStyleClass().add("speaker-notes-heading");
    section.getChildren().add(heading);
    for (MoveNote note : notes) {
      VBox move = new VBox(4);
      move.getStyleClass().add("speaker-notes-move");
      Label treePrefix = new Label(note.treePrefix());
      treePrefix.getStyleClass().add("speaker-notes-tree-prefix");
      Button san = new Button(note.moveReference());
      san.getStyleClass().add("speaker-notes-san-link");
      san.setAccessibleText("Go to " + note.moveReference());
      san.setOnAction(event -> requestSelection(note));
      HBox moveHeader = new HBox(6, treePrefix, san);
      moveHeader.setAlignment(Pos.CENTER_LEFT);
      Label comment = message(note.comment(), "speaker-notes-comment");
      comment.getStyleClass().add("speaker-notes-move-comment");
      move.getChildren().addAll(moveHeader, comment);
      section.getChildren().add(move);
    }
    return section;
  }

  private VBox storySection(List<StoryLine> lines) {
    VBox section = new VBox(12);
    section.getStyleClass().add("speaker-notes-section");
    Label heading = new Label("Story by variation");
    heading.getStyleClass().add("speaker-notes-heading");
    section.getChildren().add(heading);
    for (int index = 0; index < lines.size(); index++) {
      VBox line = new VBox(5);
      line.getStyleClass().add("speaker-notes-story-line");
      Label lineHeading = new Label("Variation " + (index + 1));
      lineHeading.getStyleClass().add("speaker-notes-story-heading");
      VBox sequence = new VBox(6);
      sequence.getStyleClass().add("speaker-notes-story-sequence");
      line.getChildren().addAll(lineHeading, sequence);
      for (MoveNote note : lines.get(index).moves()) {
        Button san = new Button(note.moveReference());
        san.getStyleClass().add("speaker-notes-san-link");
        san.setMinWidth(Region.USE_PREF_SIZE);
        san.setMaxWidth(Region.USE_PREF_SIZE);
        san.setAccessibleText("Go to " + note.moveReference());
        san.setOnAction(event -> requestSelection(note));
        HBox step = new HBox(8, san);
        step.getStyleClass().add("speaker-notes-story-step");
        step.setAlignment(Pos.TOP_LEFT);
        if (!note.comment().isBlank()) {
          Label comment = message(note.comment(), "speaker-notes-comment");
          comment.getStyleClass().add("speaker-notes-story-comment");
          HBox.setHgrow(comment, Priority.ALWAYS);
          step.getChildren().add(comment);
        }
        sequence.getChildren().add(step);
      }
      section.getChildren().add(line);
    }
    return section;
  }

  private void configureDrag(HBox header, VBox card) {
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

  private void configureResize(Label handle, VBox card) {
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

  private VBox section(String headingText, String value) {
    VBox section = new VBox(7);
    section.getStyleClass().add("speaker-notes-section");
    Label heading = new Label(headingText);
    heading.getStyleClass().add("speaker-notes-heading");
    Label body = message(value, "speaker-notes-comment");
    section.getChildren().addAll(heading, body);
    return section;
  }

  private static Label message(String text, String styleClass) {
    Label label = new Label(text == null ? "" : text);
    label.setWrapText(true);
    label.setMaxWidth(Double.MAX_VALUE);
    label.getStyleClass().add(styleClass);
    return label;
  }

  public void show() { setManaged(true); setVisible(true); toFront(); }
  public void hide() { setManaged(false); setVisible(false); }

  public EventHandler<MoveNoteSelectedEvent> getOnMoveNoteSelected() {
    return onMoveNoteSelected.get();
  }

  public void setOnMoveNoteSelected(EventHandler<MoveNoteSelectedEvent> handler) {
    onMoveNoteSelected.set(handler);
  }

  public ObjectProperty<EventHandler<MoveNoteSelectedEvent>> onMoveNoteSelectedProperty() {
    return onMoveNoteSelected;
  }

  private void requestSelection(MoveNote note) {
    EventHandler<MoveNoteSelectedEvent> handler = getOnMoveNoteSelected();
    if (handler != null) {
      handler.handle(new MoveNoteSelectedEvent(this, note));
    }
  }

  public record MoveNote(UUID nodeId, String moveReference, String comment, String treePrefix) {
    public MoveNote {
      Objects.requireNonNull(nodeId, "nodeId must not be null");
      moveReference = Objects.requireNonNullElse(moveReference, "Move");
      comment = Objects.requireNonNullElse(comment, "");
      treePrefix = Objects.requireNonNullElse(treePrefix, "");
    }
  }

  /** One complete root-to-leaf variation, presented as a sequential narrative. */
  public record StoryLine(List<MoveNote> moves) {
    public StoryLine {
      moves = List.copyOf(Objects.requireNonNull(moves, "moves must not be null"));
      if (moves.isEmpty()) {
        throw new IllegalArgumentException("moves must not be empty");
      }
    }
  }

  public static final class MoveNoteSelectedEvent extends Event {
    public static final EventType<MoveNoteSelectedEvent> MOVE_NOTE_SELECTED =
        new EventType<>(Event.ANY, "MOVE_NOTE_SELECTED");
    private final MoveNote note;

    private MoveNoteSelectedEvent(SpeakerNotesPanel source, MoveNote note) {
      super(source, NULL_SOURCE_TARGET, MOVE_NOTE_SELECTED);
      this.note = note;
    }

    public MoveNote getNote() {
      return note;
    }
  }
}
