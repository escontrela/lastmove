package com.escontrela.lastmove.ui.component.message;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Reusable modal text-input overlay styled as part of the LastMove application.
 *
 * <p>Place the control as the last child of a screen {@link StackPane}. The owning controller
 * configures its title, explanatory message, field content and button labels, then handles the
 * accepted value through {@link #getText()}. Accept does not close the modal automatically so the
 * owner can display validation feedback; cancel, close and Escape always dismiss it.
 */
public final class TextInputModal extends StackPane {

  private static final double CARD_WIDTH = 560.0;

  private final StringProperty title =
      new SimpleStringProperty(this, "title", "Enter a value");
  private final StringProperty message =
      new SimpleStringProperty(this, "message", "Provide the requested value.");
  private final StringProperty promptText =
      new SimpleStringProperty(this, "promptText", "");
  private final StringProperty acceptText =
      new SimpleStringProperty(this, "acceptText", "Accept");
  private final StringProperty cancelText =
      new SimpleStringProperty(this, "cancelText", "Cancel");
  private final ObjectProperty<EventHandler<ActionEvent>> onAccept =
      new SimpleObjectProperty<>(this, "onAccept");
  private final ObjectProperty<EventHandler<ActionEvent>> onCancel =
      new SimpleObjectProperty<>(this, "onCancel");

  private final Label titleLabel = new Label();
  private final Label messageLabel = new Label();
  private final Label validationLabel = new Label();
  private final TextField textField = new TextField();
  private final Button closeButton = new Button("×");
  private final Button acceptButton = new Button();
  private final Button cancelButton = new Button();

  public TextInputModal() {
    initialiseView();
    initialiseBehaviour();
  }

  private void initialiseView() {
    getStyleClass().addAll("message-box-overlay", "text-input-modal");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    titleLabel.getStyleClass().add("message-box-title");
    titleLabel.textProperty().bind(titleProperty());
    titleLabel.setWrapText(true);
    titleLabel.setMaxWidth(Double.MAX_VALUE);

    messageLabel.getStyleClass().add("message-box-message");
    messageLabel.textProperty().bind(messageProperty());
    messageLabel.setWrapText(true);
    messageLabel.setMaxWidth(Double.MAX_VALUE);

    textField.getStyleClass().add("text-input-modal-field");
    textField.promptTextProperty().bind(promptTextProperty());
    textField.setMaxWidth(Double.MAX_VALUE);

    validationLabel.getStyleClass().add("text-input-modal-validation");
    validationLabel.setWrapText(true);
    validationLabel.setMaxWidth(Double.MAX_VALUE);
    setValidationVisible(false);

    closeButton.getStyleClass().add("message-box-close-button");
    closeButton.setAccessibleText("Close input dialog");
    closeButton.setFocusTraversable(false);
    closeButton.setOnAction(this::handleCancel);

    acceptButton.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    acceptButton.textProperty().bind(acceptTextProperty());
    acceptButton.setDefaultButton(true);
    acceptButton.setOnAction(this::handleAccept);

    cancelButton.getStyleClass().addAll("message-box-button", "message-box-cancel-button");
    cancelButton.textProperty().bind(cancelTextProperty());
    cancelButton.setCancelButton(true);
    cancelButton.setOnAction(this::handleCancel);

    Region titleSpacer = new Region();
    HBox header = new HBox(12, titleLabel, titleSpacer, closeButton);
    header.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(titleSpacer, Priority.ALWAYS);

    Region actionSpacer = new Region();
    HBox actions = new HBox(10, actionSpacer, cancelButton, acceptButton);
    actions.setAlignment(Pos.CENTER_RIGHT);
    actions.getStyleClass().add("message-box-actions");
    HBox.setHgrow(actionSpacer, Priority.ALWAYS);

    VBox card = new VBox(12, header, messageLabel, textField, validationLabel, actions);
    card.getStyleClass().addAll("message-box-card", "text-input-modal-card");
    card.setPadding(new Insets(22));
    card.setPrefWidth(CARD_WIDTH);
    card.setMaxWidth(CARD_WIDTH);
    card.setMinHeight(Region.USE_PREF_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    getChildren().add(card);

    setVisible(false);
    setManaged(false);
  }

  private void initialiseBehaviour() {
    visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
    setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            cancelButton.fire();
            event.consume();
          }
        });
  }

  /** Displays the modal, clears previous validation and selects its complete initial value. */
  public void show() {
    clearValidationMessage();
    setManaged(true);
    setVisible(true);
    toFront();
    Platform.runLater(
        () -> {
          textField.requestFocus();
          textField.selectAll();
        });
  }

  /** Dismisses the modal without changing its configured content. */
  public void hide() {
    setVisible(false);
    setManaged(false);
  }

  /** Shows validation feedback and keeps focus in the input field. */
  public void setValidationMessage(String value) {
    validationLabel.setText(value == null ? "" : value);
    setValidationVisible(value != null && !value.isBlank());
    Platform.runLater(textField::requestFocus);
  }

  /** Removes any validation feedback from the current request. */
  public void clearValidationMessage() {
    validationLabel.setText("");
    setValidationVisible(false);
  }

  public String getText() {
    return textField.getText();
  }

  public void setText(String value) {
    textField.setText(value == null ? "" : value);
  }

  public StringProperty titleProperty() {
    return title;
  }

  public String getTitle() {
    return title.get();
  }

  public void setTitle(String value) {
    title.set(value);
  }

  public StringProperty messageProperty() {
    return message;
  }

  public String getMessage() {
    return message.get();
  }

  public void setMessage(String value) {
    message.set(value);
  }

  public StringProperty promptTextProperty() {
    return promptText;
  }

  public String getPromptText() {
    return promptText.get();
  }

  public void setPromptText(String value) {
    promptText.set(value);
  }

  public StringProperty acceptTextProperty() {
    return acceptText;
  }

  public String getAcceptText() {
    return acceptText.get();
  }

  public void setAcceptText(String value) {
    acceptText.set(value);
  }

  public StringProperty cancelTextProperty() {
    return cancelText;
  }

  public String getCancelText() {
    return cancelText.get();
  }

  public void setCancelText(String value) {
    cancelText.set(value);
  }

  public ObjectProperty<EventHandler<ActionEvent>> onAcceptProperty() {
    return onAccept;
  }

  public EventHandler<ActionEvent> getOnAccept() {
    return onAccept.get();
  }

  public void setOnAccept(EventHandler<ActionEvent> value) {
    onAccept.set(value);
  }

  public ObjectProperty<EventHandler<ActionEvent>> onCancelProperty() {
    return onCancel;
  }

  public EventHandler<ActionEvent> getOnCancel() {
    return onCancel.get();
  }

  public void setOnCancel(EventHandler<ActionEvent> value) {
    onCancel.set(value);
  }

  private void handleAccept(ActionEvent event) {
    EventHandler<ActionEvent> handler = onAccept.get();
    if (handler != null) {
      handler.handle(event);
    }
  }

  private void handleCancel(ActionEvent event) {
    hide();
    EventHandler<ActionEvent> handler = onCancel.get();
    if (handler != null) {
      handler.handle(event);
    }
  }

  private void setValidationVisible(boolean visible) {
    validationLabel.setManaged(visible);
    validationLabel.setVisible(visible);
  }
}
