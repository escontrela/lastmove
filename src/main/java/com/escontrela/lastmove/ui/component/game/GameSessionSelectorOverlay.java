package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** In-app selector for live Lichess and local computer-versus-computer sessions. */
public final class GameSessionSelectorOverlay extends StackPane {
  private static final double CARD_WIDTH = 640;
  private static final double ROW_HEIGHT = 68;

  public enum Kind { LICHESS, LOCAL_COMPUTERS }

  public record Session(Kind kind, String remoteId, GameId localId, String title, String detail) {
    @Override public String toString() { return title + "\n" + detail; }
  }

  private final ListView<Session> sessions = new ListView<>();
  private Consumer<Session> onOpen = ignored -> {};
  private Runnable onNew = () -> {};
  private Runnable onCancel = () -> {};

  public GameSessionSelectorOverlay() {
    getStyleClass().addAll("message-box-overlay", "game-session-selector-overlay");
    setAlignment(Pos.CENTER);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Label eyebrow = label("Live sessions", "eyebrow-label");
    Label title = label("Choose a game", "computer-game-setup-title");
    Label copy = label("Continue a local engine match or follow any active Lichess Arena game.",
        "computer-game-setup-description");
    copy.setWrapText(true);

    sessions.getStyleClass().add("game-session-selector-list");
    sessions.setFixedCellSize(ROW_HEIGHT);
    sessions.setFocusTraversable(false);
    sessions.setCellFactory(ignored -> new SessionCell());

    Button cancel = button("Cancel", "message-box-button", "message-box-cancel-button");
    Button create = button("New computer match", "message-box-button", "message-box-additional-button");
    Button open = button("Open selected", "message-box-button", "message-box-accept-button");
    open.disableProperty().bind(sessions.getSelectionModel().selectedItemProperty().isNull());
    cancel.setOnAction(event -> onCancel.run());
    create.setOnAction(event -> onNew.run());
    open.setOnAction(event -> {
      Session selected = sessions.getSelectionModel().getSelectedItem();
      if (selected != null) onOpen.accept(selected);
    });
    sessions.setOnMouseClicked(event -> {
      if (event.getClickCount() == 2 && sessions.getSelectionModel().getSelectedItem() != null) {
        onOpen.accept(sessions.getSelectionModel().getSelectedItem());
      }
    });

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox actions = new HBox(10, cancel, spacer, create, open);
    actions.setAlignment(Pos.CENTER_RIGHT);
    actions.getStyleClass().add("message-box-actions");

    VBox card = new VBox(12, eyebrow, title, copy, sessions, actions);
    card.setPadding(new Insets(24));
    card.setPrefWidth(CARD_WIDTH);
    card.setMinWidth(Region.USE_PREF_SIZE);
    card.setMaxWidth(CARD_WIDTH);
    card.setMinHeight(Region.USE_PREF_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.getStyleClass().addAll("message-box-card", "computer-game-setup-card");
    getChildren().add(card);

    setVisible(false);
    setManaged(false);
    visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
  }

  public void show(List<Session> values) {
    List<Session> required = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
    sessions.setItems(FXCollections.observableArrayList(required));
    int visibleRows = Math.max(1, Math.min(required.size(), 4));
    double listHeight = visibleRows * ROW_HEIGHT + 2;
    sessions.setPrefHeight(listHeight);
    sessions.setMinHeight(listHeight);
    sessions.setMaxHeight(listHeight);
    sessions.getSelectionModel().selectFirst();
    setVisible(true);
    toFront();
    Platform.runLater(sessions::requestFocus);
  }

  public void hide() { setVisible(false); setManaged(false); }
  public void setOnOpen(Consumer<Session> value) { onOpen = Objects.requireNonNull(value); }
  public void setOnNew(Runnable value) { onNew = Objects.requireNonNull(value); }
  public void setOnCancel(Runnable value) { onCancel = Objects.requireNonNull(value); }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.getStyleClass().add(style);
    return label;
  }

  private static Button button(String text, String... styles) {
    Button button = new Button(text);
    button.getStyleClass().addAll(styles);
    return button;
  }

  private final class SessionCell extends ListCell<Session> {
    private final VBox content = new VBox(2);
    private final Label title = label("", "game-session-selector-title");
    private final Label detail = label("", "game-session-selector-detail");

    private SessionCell() {
      getStyleClass().add("game-session-selector-cell");
      title.setWrapText(true);
      detail.setWrapText(true);
      content.getChildren().setAll(title, detail);
      content.setMaxWidth(Double.MAX_VALUE);
      setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      setPadding(Insets.EMPTY);
    }

    @Override protected void updateItem(Session item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        setAccessibleText(null);
        return;
      }
      title.setText(item.title());
      detail.setText(item.detail());
      setAccessibleText(item.title() + " " + item.detail());
      setGraphic(content);
    }
  }
}
