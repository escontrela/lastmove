package com.escontrela.lastmove.ui.component.comment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  private final Button closeButton = new Button("×");

  public SpeakerNotesPanel() {
    getStyleClass().add("speaker-notes-overlay");
    setAlignment(Pos.CENTER_RIGHT);

    Label title = new Label("PGN speaker notes");
    title.getStyleClass().add("speaker-notes-title");
    closeButton.getStyleClass().add("speaker-notes-close");
    closeButton.setAccessibleText("Close PGN speaker notes");
    closeButton.setOnAction(event -> hide());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(10, title, spacer, closeButton);
    header.setAlignment(Pos.CENTER_LEFT);

    content.getStyleClass().add("speaker-notes-content");
    content.setPadding(new Insets(2, 4, 2, 2));
    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.getStyleClass().add("speaker-notes-scroll");
    VBox.setVgrow(scrollPane, Priority.ALWAYS);

    VBox card = new VBox(16, header, scrollPane);
    card.getStyleClass().add("speaker-notes-card");
    card.setPadding(new Insets(22));
    card.setPrefWidth(420);
    card.setMaxWidth(420);
    card.setPrefHeight(560);
    card.setMaxHeight(560);
    getChildren().add(card);
    hide();
  }

  public void showNotes(String studyComment, String chapterTitle, String chapterComment, List<MoveNote> notes) {
    Objects.requireNonNull(notes, "notes must not be null");
    List<javafx.scene.Node> sections = new ArrayList<>();
    addSection(sections, "Study comment", studyComment);
    addSection(sections, "Chapter comment · " + chapterTitle, chapterComment);
    if (!notes.isEmpty()) {
      sections.add(movesSection(notes));
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
      move.setStyle("-fx-padding: 0 0 0 " + (note.variationDepth() * 14) + ";");
      Label san = new Label(note.moveReference());
      san.getStyleClass().add("speaker-notes-san");
      Label comment = message(note.comment(), "speaker-notes-comment");
      move.getChildren().addAll(san, comment);
      section.getChildren().add(move);
    }
    return section;
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
  public record MoveNote(String moveReference, String comment, int variationDepth) {
    public MoveNote {
      moveReference = Objects.requireNonNullElse(moveReference, "Move");
      comment = Objects.requireNonNullElse(comment, "");
      if (variationDepth < 0) throw new IllegalArgumentException("variationDepth must not be negative");
    }
  }
}
