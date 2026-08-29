package com.escontrela.lastmove.ui.component.evaluation;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Compact in-screen modal for choosing the analysis engine. */
public final class EngineSelectorModal extends StackPane {
  private final Label title = new Label("Choose engine");
  private final Label message = new Label("Select the engine used for position evaluation.");
  private final FlowPane options = new FlowPane(10, 10);
  private final VBox card = new VBox(14);
  private EventHandler<ActionEvent> onCancel;
  private EventHandler<EngineSelectionEvent> onEngineSelected;

  public EngineSelectorModal() {
    getStyleClass().addAll("message-box-overlay", "engine-selector-modal");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setFocusTraversable(true);
    setOnKeyPressed(this::handleEscape);
    title.getStyleClass().add("message-box-title");
    message.getStyleClass().add("message-box-message");
    Button close = new Button("×");
    close.getStyleClass().add("message-box-close-button");
    close.setOnAction(this::cancel);
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    HBox header = new HBox(12, title, spacer, close);
    header.setAlignment(Pos.CENTER_LEFT);
    Button cancel = new Button("Cancel");
    cancel.getStyleClass().addAll("message-box-button", "message-box-cancel-button");
    cancel.setCancelButton(true);
    cancel.setOnAction(this::cancel);
    HBox actions = new HBox(cancel);
    actions.setAlignment(Pos.CENTER_RIGHT);
    actions.getStyleClass().add("message-box-actions");
    card.getChildren().addAll(header, message, options, actions);
    card.getStyleClass().addAll("message-box-card", "engine-selector-modal-card");
    card.setPadding(new Insets(18));
    card.setMinHeight(Region.USE_PREF_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setMaxWidth(Region.USE_PREF_SIZE);
    getChildren().add(card);
    setVisible(false);
    setManaged(false);
  }

  public void show(List<ComputerEngineDescriptor> engines, String selectedId) {
    show(engines, selectedId, "Choose engine", "Select the engine used for position evaluation.");
  }

  /** Shows the existing engine picker with wording suitable for another engine-scoped workflow. */
  public void show(List<ComputerEngineDescriptor> engines, String selectedId, String titleText, String messageText) {
    Objects.requireNonNull(engines, "engines");
    title.setText(Objects.requireNonNull(titleText, "titleText"));
    message.setText(Objects.requireNonNull(messageText, "messageText"));
    options.getChildren().setAll(engines.stream().map(engine -> option(engine, selectedId)).toList());
    setManaged(true);
    setVisible(true);
    toFront();
    Platform.runLater(this::requestFocus);
  }

  public void hide() { setVisible(false); setManaged(false); }
  public void setOnCancel(EventHandler<ActionEvent> handler) { onCancel = handler; }
  public void setOnEngineSelected(EventHandler<EngineSelectionEvent> handler) { onEngineSelected = handler; }

  private Button option(ComputerEngineDescriptor engine, String selectedId) {
    Label icon = new Label("♞"); icon.getStyleClass().add("engine-selector-icon");
    Label name = new Label(engine.displayName()); name.getStyleClass().add("engine-selector-name");
    Label version = new Label(engine.version()); version.getStyleClass().add("engine-selector-version");
    Button choice = new Button();
    choice.getStyleClass().add("engine-selector-tile");
    if (engine.id().equals(selectedId)) choice.getStyleClass().add("selected");
    VBox details = new VBox(4, icon, name, version);
    details.setAlignment(Pos.CENTER);
    choice.setGraphic(ComputerEngineIds.KNIGHTSHADE.equals(engine.id())
        ? knightshadeTileGraphic(details)
        : details);
    choice.setOnAction(event -> {
      if (onEngineSelected != null) onEngineSelected.handle(new EngineSelectionEvent(engine.id()));
      hide();
    });
    return choice;
  }

  private StackPane knightshadeTileGraphic(VBox details) {
    ImageView mark = new ImageView(loadKnightshadeMark());
    mark.setFitWidth(74);
    mark.setFitHeight(74);
    mark.setPreserveRatio(true);
    mark.setMouseTransparent(true);
    mark.setOpacity(0.2);
    mark.getStyleClass().add("engine-selector-knightshade-mark");
    return new StackPane(mark, details);
  }

  private Image loadKnightshadeMark() {
    boolean nightMode = getScene() != null
        && getScene().getRoot().getStyleClass().contains("night-mode");
    String resource = nightMode
        ? "/images/knightshade-engine-mark-dark.png"
        : "/images/knightshade-engine-mark.png";
    return new Image(Objects.requireNonNull(getClass().getResource(resource),
        () -> "Missing Knightshade engine mark: " + resource).toExternalForm());
  }

  private void handleEscape(KeyEvent event) {
    if (event.getCode() == KeyCode.ESCAPE) {
      cancel(new ActionEvent(this, this));
      event.consume();
    }
  }

  private void cancel(ActionEvent event) { hide(); if (onCancel != null) onCancel.handle(event); }

  public static final class EngineSelectionEvent extends ActionEvent {
    private final String engineId;
    private EngineSelectionEvent(String engineId) { this.engineId = engineId; }
    public String engineId() { return engineId; }
  }
}
