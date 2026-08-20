package com.escontrela.lastmove.ui.component.context;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Reusable in-screen context menu panel.
 *
 * <p>Add this control as the last child of a {@link StackPane}. The owning screen registers its
 * actions with {@link #addItem(String, String, EventHandler)} and opens it from a
 * {@code CONTEXT_MENU_REQUESTED} event. It is intentionally an in-scene panel rather than a
 * native {@code ContextMenu}, so it shares the application's light and night-mode styling.
 */
public class ContextualMenuPanel extends StackPane {

    private static final double SCREEN_MARGIN = 12.0;

    private final VBox menuCard = new VBox(2.0);
    private final VBox menuContent = new VBox(2.0);
    private final Button closeButton = new Button("×");

    public ContextualMenuPanel() {
        getStyleClass().add("context-menu-panel");
        setAlignment(Pos.TOP_LEFT);
        setPickOnBounds(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setFocusTraversable(true);

        menuCard.getStyleClass().add("context-menu-card");
        menuCard.setPadding(new Insets(6.0));
        menuCard.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        closeButton.getStyleClass().add("context-menu-close-button");
        closeButton.setAccessibleText("Close context menu");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(event -> hide());
        Region headerSpacer = new Region();
        HBox header = new HBox(headerSpacer, closeButton);
        header.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        menuCard.getChildren().setAll(header, menuContent);
        getChildren().add(menuCard);

        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getTarget() == this) {
                hide();
            }
        });
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
        hide();
    }

    /** Removes all configured menu entries. */
    public void clearItems() {
        menuContent.getChildren().clear();
    }

    /** Adds a command with an optional shortcut hint. */
    public void addItem(String text, String shortcut, EventHandler<ActionEvent> action) {
        addItem(text, shortcut, false, action);
    }

    /** Adds a command with an optional shortcut hint and disabled state. */
    public void addItem(
            String text,
            String shortcut,
            boolean disabled,
            EventHandler<ActionEvent> action) {
        Button item = new Button();
        item.getStyleClass().add("context-menu-item");
        item.setMaxWidth(Double.MAX_VALUE);
        item.setMnemonicParsing(false);

        Label itemText = new Label(text);
        itemText.getStyleClass().add("context-menu-item-text");
        Label shortcutText = new Label(shortcut == null ? "" : shortcut);
        shortcutText.getStyleClass().add("context-menu-item-shortcut");
        Region spacer = new Region();
        HBox content = new HBox(18.0, itemText, spacer, shortcutText);
        content.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        item.setGraphic(content);
        item.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        item.setDisable(disabled);
        item.setOnAction(event -> {
            hide();
            if (action != null) {
                action.handle(event);
            }
        });
        menuContent.getChildren().add(item);
    }

    /** Adds a visual separator between groups of commands. */
    public void addSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("context-menu-separator");
        menuContent.getChildren().add(separator);
    }

    /** Shows the panel at the supplied scene coordinates. */
    public void showAtScene(double sceneX, double sceneY) {
        Point2D localPoint = sceneToLocal(sceneX, sceneY);
        showAt(localPoint.getX(), localPoint.getY());
    }

    /** Shows the panel at coordinates relative to this control. */
    public void showAt(double x, double y) {
        setManaged(true);
        setVisible(true);
        toFront();
        forceOverlayLayout();
        Platform.runLater(() -> {
            menuCard.applyCss();
            double menuWidth = menuCard.prefWidth(-1.0);
            double menuHeight = menuCard.prefHeight(menuWidth);
            double panelWidth = Math.max(Math.max(getWidth(), getLayoutBounds().getWidth()), parentWidth());
            double panelHeight = Math.max(Math.max(getHeight(), getLayoutBounds().getHeight()), parentHeight());
            double maximumX = Math.max(SCREEN_MARGIN, panelWidth - menuWidth - SCREEN_MARGIN);
            double maximumY = Math.max(SCREEN_MARGIN, panelHeight - menuHeight - SCREEN_MARGIN);
            menuCard.setTranslateX(clamp(x, SCREEN_MARGIN, maximumX));
            menuCard.setTranslateY(clamp(y, SCREEN_MARGIN, maximumY));
            requestFocus();
        });
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    private void forceOverlayLayout() {
        Parent parent = getParent();
        if (parent != null) {
            parent.applyCss();
            parent.layout();
        }
        applyCss();
        layout();
    }

    private double parentWidth() {
        Parent parent = getParent();
        return parent == null ? 0.0 : parent.getLayoutBounds().getWidth();
    }

    private double parentHeight() {
        Parent parent = getParent();
        return parent == null ? 0.0 : parent.getLayoutBounds().getHeight();
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
