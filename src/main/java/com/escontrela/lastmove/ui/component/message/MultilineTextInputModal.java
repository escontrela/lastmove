package com.escontrela.lastmove.ui.component.message;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Reusable MessageBox-style multiline editor. An empty value represents deletion. */
public final class MultilineTextInputModal extends StackPane {
  private final Label title = new Label("Edit comment");
  private final TextArea input = new TextArea();
  private final Button save = new Button("Save");
  private EventHandler<ActionEvent> onSave;

  public MultilineTextInputModal() {
    getStyleClass().addAll("message-box-overlay", "multiline-input-modal");
    setAlignment(Pos.CENTER);
    title.getStyleClass().add("message-box-title");
    input.getStyleClass().add("multiline-input-area");
    input.setWrapText(true);
    input.setPrefRowCount(8);
    Button cancel = new Button("Cancel");
    Button close = new Button("×");
    close.getStyleClass().add("message-box-close-button");
    save.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    cancel.getStyleClass().addAll("message-box-button", "message-box-cancel-button");
    save.setOnAction(event -> { if (onSave != null) onSave.handle(event); });
    cancel.setOnAction(event -> hide()); close.setOnAction(event -> hide());
    Region headerSpace = new Region(); HBox.setHgrow(headerSpace, Priority.ALWAYS);
    HBox header = new HBox(10, title, headerSpace, close);
    Region actionSpace = new Region(); HBox.setHgrow(actionSpace, Priority.ALWAYS);
    HBox actions = new HBox(10, actionSpace, cancel, save); actions.setAlignment(Pos.CENTER_RIGHT);
    VBox card = new VBox(14, header, input, actions);
    card.getStyleClass().addAll("message-box-card", "multiline-input-card");
    card.setPadding(new Insets(22)); card.setPrefWidth(580); card.setMaxWidth(580);
    getChildren().add(card); hide();
  }
  public void show(String heading, String value, EventHandler<ActionEvent> handler) {
    title.setText(heading); input.setText(value == null ? "" : value); onSave = handler;
    setManaged(true); setVisible(true); toFront(); Platform.runLater(input::requestFocus);
  }
  public void hide() { setManaged(false); setVisible(false); }
  public String getText() { return input.getText(); }
}
