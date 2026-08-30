package com.escontrela.lastmove.ui.component.player;

import com.escontrela.lastmove.application.player.PlayerSummary;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** Reusable in-screen picker for a persisted local or system player profile. */
public final class PlayerSelectorModal extends StackPane {
  private final Label title = new Label("Choose player");
  private final Label message = new Label("Select the player whose data you want to view.");
  private final CheckBox onlyApplicationUsers = new CheckBox("Only app users");
  private final FlowPane options = new FlowPane(10, 10);
  private final VBox card = new VBox(14);
  private EventHandler<ActionEvent> onCancel;
  private EventHandler<PlayerSelectionEvent> onPlayerSelected;
  private List<PlayerSummary> availablePlayers = List.of();
  private Long selectedPlayerId;
  private Optional<String> knightshadeLichessId = Optional.empty();

  public PlayerSelectorModal() {
    getStyleClass().addAll("message-box-overlay", "player-selector-modal");
    setAlignment(Pos.CENTER); setPickOnBounds(true); setFocusTraversable(true); setOnKeyPressed(this::handleEscape);
    title.getStyleClass().add("message-box-title"); message.getStyleClass().add("message-box-message");
    onlyApplicationUsers.getStyleClass().add("player-selector-filter");
    onlyApplicationUsers.setSelected(true); onlyApplicationUsers.setOnAction(event -> renderOptions());
    Button close = new Button("×"); close.getStyleClass().add("message-box-close-button"); close.setOnAction(this::cancel);
    Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(12, title, spacer, close); header.setAlignment(Pos.CENTER_LEFT);
    Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("message-box-button", "message-box-cancel-button"); cancel.setCancelButton(true); cancel.setOnAction(this::cancel);
    HBox actions = new HBox(cancel); actions.setAlignment(Pos.CENTER_RIGHT); actions.getStyleClass().add("message-box-actions");
    card.getChildren().addAll(header, message, onlyApplicationUsers, options, actions); card.getStyleClass().addAll("message-box-card", "player-selector-modal-card"); card.setPadding(new Insets(18));
    card.setMinHeight(Region.USE_PREF_SIZE); card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setMinWidth(Region.USE_PREF_SIZE); card.setMaxWidth(720);
    getChildren().add(card); setVisible(false); setManaged(false);
  }

  public void show(List<PlayerSummary> players, Long selectedPlayerId, String titleText, String messageText) {
    show(players, selectedPlayerId, titleText, messageText, Optional.empty());
  }

  /** Shows local players and, when requested, external Lichess participants. */
  public void show(List<PlayerSummary> players, Long selectedPlayerId, String titleText, String messageText,
      Optional<String> knightshadeLichessId) {
    Objects.requireNonNull(players, "players must not be null");
    title.setText(Objects.requireNonNull(titleText, "titleText must not be null"));
    message.setText(Objects.requireNonNull(messageText, "messageText must not be null"));
    this.availablePlayers = List.copyOf(players);
    this.selectedPlayerId = selectedPlayerId;
    this.knightshadeLichessId = Objects.requireNonNull(knightshadeLichessId, "knightshadeLichessId must not be null");
    onlyApplicationUsers.setSelected(true);
    renderOptions();
    setManaged(true); setVisible(true); toFront(); Platform.runLater(this::requestFocus);
  }

  public void setOnCancel(EventHandler<ActionEvent> handler) { onCancel = handler; }
  public void setOnPlayerSelected(EventHandler<PlayerSelectionEvent> handler) { onPlayerSelected = handler; }

  private void renderOptions() {
    options.getChildren().setAll(availablePlayers.stream()
        .filter(player -> !onlyApplicationUsers.isSelected() || isApplicationPlayer(player))
        .map(player -> option(player, selectedPlayerId)).toList());
  }

  private boolean isApplicationPlayer(PlayerSummary player) {
    if (!player.systemPlayer()) return true;
    return player.externalProvider().filter("LICHESS"::equalsIgnoreCase).isPresent()
        && player.externalAccountId().filter(id -> knightshadeLichessId.filter(id::equalsIgnoreCase).isPresent()).isPresent();
  }

  private Button option(PlayerSummary player, Long selectedPlayerId) {
    ImageView avatar = avatar(player); Label name = new Label(player.fullName()); name.getStyleClass().add("player-selector-name");
    Label detail = new Label(detail(player)); detail.getStyleClass().add("player-selector-detail");
    VBox details = new VBox(5, avatar, name, detail); details.setAlignment(Pos.CENTER);
    Button choice = new Button(); choice.setGraphic(details); choice.getStyleClass().add("player-selector-tile");
    if (selectedPlayerId != null && player.id().value() == selectedPlayerId) choice.getStyleClass().add("selected");
    choice.setOnAction(event -> { if (onPlayerSelected != null) onPlayerSelected.handle(new PlayerSelectionEvent(player)); hide(); });
    return choice;
  }

  private String detail(PlayerSummary player) {
    if (!player.systemPlayer()) return "Local player";
    return isApplicationPlayer(player) ? "Knightshade bot" : "Lichess player";
  }

  private ImageView avatar(PlayerSummary player) {
    ImageView view = new ImageView(player.photo().map(bytes -> new Image(new ByteArrayInputStream(bytes))).orElse(null));
    view.setFitWidth(56); view.setFitHeight(56); view.setPreserveRatio(true); view.setClip(new Circle(28, 28, 28));
    view.getStyleClass().add("player-selector-avatar");
    if (view.getImage() == null) {
      String resource = player.systemPlayer() ? "/images/knightshade-engine-mark.png" : "/images/face_35dp_000000.png";
      view.setImage(new Image(Objects.requireNonNull(getClass().getResource(resource)).toExternalForm()));
    }
    return view;
  }

  private void hide() { setVisible(false); setManaged(false); }
  private void handleEscape(KeyEvent event) { if (event.getCode() == KeyCode.ESCAPE) { cancel(new ActionEvent(this, this)); event.consume(); } }
  private void cancel(ActionEvent event) { hide(); if (onCancel != null) onCancel.handle(event); }

  public static final class PlayerSelectionEvent extends ActionEvent {
    private final PlayerSummary player;
    private PlayerSelectionEvent(PlayerSummary player) { this.player = player; }
    public PlayerSummary player() { return player; }
  }
}
