package com.escontrela.lastmove.ui.component.comment;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Reusable, domain-agnostic side overlay for displaying a text annotation. */
public final class CommentPanel extends StackPane {
  private final StringProperty title = new SimpleStringProperty(this, "title", "Comment");
  private final StringProperty content = new SimpleStringProperty(this, "content", "");
  private final Label contentLabel = new Label();
  private final Label emptyLabel = new Label("No comment yet.");
  private final Button editButton = new Button("Edit");
  private final Button closeButton = new Button("×");

  public CommentPanel() {
    getStyleClass().add("comment-panel-overlay");
    setAlignment(Pos.CENTER_RIGHT);
    Label titleLabel = new Label();
    titleLabel.textProperty().bind(title);
    titleLabel.getStyleClass().add("comment-panel-title");
    closeButton.getStyleClass().add("comment-panel-close");
    closeButton.setAccessibleText("Close comment");
    closeButton.setOnAction(event -> hide());
    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(10, titleLabel, spacer, closeButton);
    header.setAlignment(Pos.CENTER_LEFT);
    contentLabel.textProperty().bind(content);
    contentLabel.setWrapText(true);
    contentLabel.getStyleClass().add("comment-panel-content");
    emptyLabel.getStyleClass().add("comment-panel-empty");
    editButton.getStyleClass().add("comment-panel-edit");
    editButton.setText("Edit comment");
    VBox card = new VBox(16, header, contentLabel, emptyLabel, editButton);
    card.getStyleClass().add("comment-panel-card");
    card.setPadding(new Insets(22));
    card.setPrefWidth(360);
    card.setMaxWidth(360);
    getChildren().add(card);
    content.addListener((obs, old, value) -> updateEmptyState());
    updateEmptyState();
    hide();
  }

  private void updateEmptyState() {
    boolean empty = getContent() == null || getContent().isBlank();
    contentLabel.setVisible(!empty); contentLabel.setManaged(!empty);
    emptyLabel.setVisible(empty); emptyLabel.setManaged(empty);
  }
  public void show() { setManaged(true); setVisible(true); toFront(); }
  public void hide() { setManaged(false); setVisible(false); }
  public StringProperty titleProperty() { return title; }
  public void setTitle(String value) { title.set(value); }
  public String getTitle() { return title.get(); }
  public StringProperty contentProperty() { return content; }
  public void setContent(String value) { content.set(value == null ? "" : value); }
  public String getContent() { return content.get(); }
  public void setOnEdit(EventHandler<ActionEvent> handler) { editButton.setOnAction(handler); }
  public void setOnClose(EventHandler<ActionEvent> handler) { closeButton.setOnAction(event -> { hide(); if (handler != null) handler.handle(event); }); }
}
