package com.escontrela.lastmove.ui.component.message;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Reusable in-screen message box with configurable actions.
 *
 * <p>The control is not an application screen. Add it as the last child of a {@link StackPane}
 * in any FXML view so it can overlay that view's content, then call {@link #show()} and
 * {@link #hide()} from the owning controller.
 */
public class MessageBox extends StackPane {

    private static final double DEFAULT_MAX_CARD_WIDTH = 560.0;

    private final StringProperty title =
            new SimpleStringProperty(this, "title", "Confirmation");
    private final StringProperty message =
            new SimpleStringProperty(this, "message", "Do you want to continue?");
    private final StringProperty acceptText =
            new SimpleStringProperty(this, "acceptText", "Accept");
    private final StringProperty cancelText =
            new SimpleStringProperty(this, "cancelText", "Cancel");
    private final ObjectProperty<MessageBoxButtonMode> buttonMode =
            new SimpleObjectProperty<>(this, "buttonMode", MessageBoxButtonMode.ACCEPT_CANCEL);
    private final BooleanProperty autoHide =
            new SimpleBooleanProperty(this, "autoHide", true);
    private final ObjectProperty<EventHandler<ActionEvent>> onAccept =
            new SimpleObjectProperty<>(this, "onAccept");
    private final ObjectProperty<EventHandler<ActionEvent>> onCancel =
            new SimpleObjectProperty<>(this, "onCancel");

    private final Label titleLabel = new Label();
    private final Label messageLabel = new Label();
    private final Button acceptButton = new Button();
    private final Button cancelButton = new Button();

    public MessageBox() {
        initialiseView();
        initialiseBehaviour();
    }

    private void initialiseView() {
        getStyleClass().add("message-box-overlay");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        titleLabel.getStyleClass().add("message-box-title");
        titleLabel.textProperty().bind(titleProperty());
        titleLabel.setWrapText(true);

        messageLabel.getStyleClass().add("message-box-message");
        messageLabel.textProperty().bind(messageProperty());
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);

        acceptButton.getStyleClass().addAll("message-box-button", "message-box-accept-button");
        acceptButton.textProperty().bind(acceptTextProperty());
        acceptButton.setDefaultButton(true);
        acceptButton.setOnAction(this::handleAccept);

        cancelButton.getStyleClass().addAll("message-box-button", "message-box-cancel-button");
        cancelButton.textProperty().bind(cancelTextProperty());
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(this::handleCancel);

        Region actionSpacer = new Region();
        HBox actions = new HBox(10, actionSpacer, cancelButton, acceptButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("message-box-actions");
        HBox.setHgrow(actionSpacer, javafx.scene.layout.Priority.ALWAYS);

        VBox card = new VBox(14, titleLabel, messageLabel, actions);
        card.setMaxWidth(DEFAULT_MAX_CARD_WIDTH);
        card.getStyleClass().add("message-box-card");
        getChildren().add(card);

        setVisible(false);
        setManaged(false);
    }

    private void initialiseBehaviour() {
        visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
        buttonMode.addListener((ignored, oldValue, newValue) -> updateButtonVisibility());
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && cancelButton.isVisible()) {
                cancelButton.fire();
                event.consume();
            }
        });
        updateButtonVisibility();
    }

    public void show() {
        setManaged(true);
        setVisible(true);
        toFront();
        Platform.runLater(() -> {
            requestFocus();
            if (acceptButton.isVisible()) {
                acceptButton.requestFocus();
            } else if (cancelButton.isVisible()) {
                cancelButton.requestFocus();
            }
        });
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    public final StringProperty titleProperty() {
        return title;
    }

    public final String getTitle() {
        return title.get();
    }

    public final void setTitle(String value) {
        title.set(value);
    }

    public final StringProperty messageProperty() {
        return message;
    }

    public final String getMessage() {
        return message.get();
    }

    public final void setMessage(String value) {
        message.set(value);
    }

    public final StringProperty acceptTextProperty() {
        return acceptText;
    }

    public final String getAcceptText() {
        return acceptText.get();
    }

    public final void setAcceptText(String value) {
        acceptText.set(value);
    }

    public final StringProperty cancelTextProperty() {
        return cancelText;
    }

    public final String getCancelText() {
        return cancelText.get();
    }

    public final void setCancelText(String value) {
        cancelText.set(value);
    }

    public final ObjectProperty<MessageBoxButtonMode> buttonModeProperty() {
        return buttonMode;
    }

    public final MessageBoxButtonMode getButtonMode() {
        return buttonMode.get();
    }

    public final void setButtonMode(MessageBoxButtonMode value) {
        buttonMode.set(value == null ? MessageBoxButtonMode.NONE : value);
    }

    public final BooleanProperty autoHideProperty() {
        return autoHide;
    }

    public final boolean isAutoHide() {
        return autoHide.get();
    }

    public final void setAutoHide(boolean value) {
        autoHide.set(value);
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onAcceptProperty() {
        return onAccept;
    }

    public final EventHandler<ActionEvent> getOnAccept() {
        return onAccept.get();
    }

    public final void setOnAccept(EventHandler<ActionEvent> value) {
        onAccept.set(value);
    }

    public final ObjectProperty<EventHandler<ActionEvent>> onCancelProperty() {
        return onCancel;
    }

    public final EventHandler<ActionEvent> getOnCancel() {
        return onCancel.get();
    }

    public final void setOnCancel(EventHandler<ActionEvent> value) {
        onCancel.set(value);
    }

    private void updateButtonVisibility() {
        MessageBoxButtonMode mode = getButtonMode();
        boolean showAccept = mode == MessageBoxButtonMode.ACCEPT
                || mode == MessageBoxButtonMode.ACCEPT_CANCEL;
        boolean showCancel = mode == MessageBoxButtonMode.CANCEL
                || mode == MessageBoxButtonMode.ACCEPT_CANCEL;
        setButtonVisible(acceptButton, showAccept);
        setButtonVisible(cancelButton, showCancel);
    }

    private void setButtonVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private void handleAccept(ActionEvent event) {
        if (isAutoHide()) {
            hide();
        }
        EventHandler<ActionEvent> handler = getOnAccept();
        if (handler != null) {
            handler.handle(event);
        }
    }

    private void handleCancel(ActionEvent event) {
        if (isAutoHide()) {
            hide();
        }
        EventHandler<ActionEvent> handler = getOnCancel();
        if (handler != null) {
            handler.handle(event);
        }
    }
}
