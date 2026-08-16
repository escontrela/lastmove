package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerGameConfiguration;
import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.game.TimeControl;
import com.escontrela.lastmove.domain.notation.Fen;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * In-screen setup overlay used exclusively before a human-versus-computer game.
 *
 * <p>The overlay owns presentation and selection state only. It returns a complete {@link
 * ComputerGameConfiguration}; starting the engine and creating a game remain responsibilities of
 * the owning screen controller and application service.
 */
public final class HumanVsComputerSetupOverlay extends StackPane {

  private static final String DEFAULT_HUMAN_NAME = "Player";
  private static final Duration DEFAULT_ENGINE_THINKING_TIME = Duration.ofMillis(500);

  private final ComboBox<ComputerEngineDescriptor> engineSelector = new ComboBox<>();
  private final ComboBox<TimePreset> timeSelector = new ComboBox<>();
  private final ToggleButton whiteButton = new ToggleButton("White");
  private final ToggleButton blackButton = new ToggleButton("Black");
  private final ToggleButton initialPositionButton = new ToggleButton("Initial position");
  private final ToggleButton fenPositionButton = new ToggleButton("FEN");
  private final TextField fenField = new TextField();
  private final VBox fenInput = new VBox(8);
  private final Button cancelButton = new Button("Cancel");
  private final Button startButton = new Button("Start game");
  private final Label validationLabel = new Label();
  private final ObjectProperty<EventHandler<StartGameEvent>> onStartGame =
      new SimpleObjectProperty<>(this, "onStartGame");
  private final ObjectProperty<EventHandler<ActionEvent>> onCancel =
      new SimpleObjectProperty<>(this, "onCancel");

  public HumanVsComputerSetupOverlay() {
    initialiseView();
    initialiseBehaviour();
  }

  private void initialiseView() {
    getStyleClass().addAll("message-box-overlay", "computer-game-setup-overlay");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Label eyebrow = new Label("New progressive game");
    eyebrow.getStyleClass().add("eyebrow-label");
    Label title = new Label("Human vs computer");
    title.getStyleClass().add("computer-game-setup-title");
    Label description =
        new Label(
            "Choose an opponent, your colour, the clock and the position where play begins.");
    description.setWrapText(true);
    description.getStyleClass().add("computer-game-setup-description");

    engineSelector.setMaxWidth(Double.MAX_VALUE);
    engineSelector.getStyleClass().add("computer-game-setup-combo");
    engineSelector.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(ComputerEngineDescriptor descriptor) {
            return descriptor == null
                ? ""
                : descriptor.displayName() + " " + descriptor.version();
          }

          @Override
          public ComputerEngineDescriptor fromString(String value) {
            throw new UnsupportedOperationException("The engine selector is not editable");
          }
        });

    ToggleGroup colorGroup = new ToggleGroup();
    whiteButton.setToggleGroup(colorGroup);
    blackButton.setToggleGroup(colorGroup);
    whiteButton.setUserData(PieceColor.WHITE);
    blackButton.setUserData(PieceColor.BLACK);
    whiteButton.setSelected(true);
    whiteButton.getStyleClass().add("computer-game-color-button");
    blackButton.getStyleClass().add("computer-game-color-button");
    HBox colors = new HBox(10, whiteButton, blackButton);
    colors.setAlignment(Pos.CENTER_LEFT);

    ToggleGroup positionGroup = new ToggleGroup();
    initialPositionButton.setToggleGroup(positionGroup);
    fenPositionButton.setToggleGroup(positionGroup);
    initialPositionButton.setSelected(true);
    initialPositionButton.getStyleClass().add("computer-game-color-button");
    fenPositionButton.getStyleClass().add("computer-game-color-button");
    HBox startingPositions = new HBox(10, initialPositionButton, fenPositionButton);
    startingPositions.setAlignment(Pos.CENTER_LEFT);
    fenField.setPromptText("Paste a complete FEN position");
    fenField.setMaxWidth(Double.MAX_VALUE);
    fenField.getStyleClass().add("settings-text-field");
    fenInput.getChildren().setAll(fieldLabel("FEN position"), fenField);
    fenInput.setVisible(false);
    fenInput.setManaged(false);
    positionGroup.selectedToggleProperty().addListener(
        (ignored, previous, selected) -> {
          if (selected == null) {
            initialPositionButton.setSelected(true);
            return;
          }
          boolean fenSelected = selected == fenPositionButton;
          fenInput.setManaged(fenSelected);
          fenInput.setVisible(fenSelected);
          if (fenSelected) {
            Platform.runLater(fenField::requestFocus);
          }
        });

    timeSelector.setItems(FXCollections.observableArrayList(TimePreset.values()));
    timeSelector.getSelectionModel().select(TimePreset.TEN_MINUTES);
    timeSelector.setMaxWidth(Double.MAX_VALUE);
    timeSelector.getStyleClass().add("computer-game-setup-combo");

    validationLabel.setWrapText(true);
    validationLabel.setMaxWidth(Double.MAX_VALUE);
    validationLabel.getStyleClass().add("computer-game-setup-validation");

    cancelButton.getStyleClass().add("secondary-button");
    startButton.getStyleClass().add("primary-button");
    startButton.setDefaultButton(true);
    cancelButton.setCancelButton(true);
    Region actionSpacer = new Region();
    HBox.setHgrow(actionSpacer, Priority.ALWAYS);
    HBox actions = new HBox(10, actionSpacer, cancelButton, startButton);
    actions.setAlignment(Pos.CENTER_RIGHT);

    VBox card =
        new VBox(
            12,
            eyebrow,
            title,
            description,
            fieldLabel("Computer opponent"),
            engineSelector,
            fieldLabel("Play as"),
            colors,
            fieldLabel("Time control"),
            timeSelector,
            fieldLabel("Starting position"),
            startingPositions,
            fenInput,
            validationLabel,
            actions);
    card.setPadding(new Insets(28));
    card.setMaxWidth(520);
    card.setMinHeight(Region.USE_PREF_SIZE);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.getStyleClass().add("computer-game-setup-card");
    getChildren().add(card);
    setVisible(false);
    setManaged(false);
  }

  private void initialiseBehaviour() {
    visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
    cancelButton.setOnAction(this::cancel);
    startButton.setOnAction(this::startGame);
    setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            cancel(new ActionEvent(this, this));
            event.consume();
          }
        });
  }

  /** Shows fresh setup state for the supplied application-provided engines. */
  public void show(List<ComputerEngineDescriptor> engines) {
    List<ComputerEngineDescriptor> required =
        List.copyOf(Objects.requireNonNull(engines, "engines must not be null"));
    engineSelector.setItems(FXCollections.observableArrayList(required));
    engineSelector.getSelectionModel().selectFirst();
    whiteButton.setSelected(true);
    timeSelector.getSelectionModel().select(TimePreset.TEN_MINUTES);
    initialPositionButton.setSelected(true);
    fenField.clear();
    setBusy(false);
    validationLabel.setText(required.isEmpty() ? "No computer engine is configured." : "");
    startButton.setDisable(required.isEmpty());
    setManaged(true);
    setVisible(true);
    toFront();
    Platform.runLater(engineSelector::requestFocus);
  }

  /** Keeps the overlay visible while its owner starts the external engine. */
  public void setBusy(boolean busy) {
    engineSelector.setDisable(busy);
    timeSelector.setDisable(busy);
    whiteButton.setDisable(busy);
    blackButton.setDisable(busy);
    initialPositionButton.setDisable(busy);
    fenPositionButton.setDisable(busy);
    fenField.setDisable(busy);
    cancelButton.setDisable(busy);
    startButton.setDisable(busy || engineSelector.getItems().isEmpty());
    startButton.setText(busy ? "Starting…" : "Start game");
    if (busy) {
      validationLabel.setText("Starting the computer engine…");
    }
  }

  /** Displays a recoverable engine or validation failure inside the overlay. */
  public void showError(String message) {
    setBusy(false);
    validationLabel.setText(Objects.requireNonNullElse(message, "Unable to start the game"));
  }

  public void hide() {
    setVisible(false);
    setManaged(false);
  }

  public void setOnStartGame(EventHandler<StartGameEvent> handler) {
    onStartGame.set(handler);
  }

  public void setOnCancel(EventHandler<ActionEvent> handler) {
    onCancel.set(handler);
  }

  private void startGame(ActionEvent ignored) {
    ComputerEngineDescriptor engine = engineSelector.getValue();
    TimePreset time = timeSelector.getValue();
    PieceColor color = blackButton.isSelected() ? PieceColor.BLACK : PieceColor.WHITE;
    if (engine == null || time == null) {
      validationLabel.setText("Choose an opponent and time control.");
      return;
    }
    Optional<Fen> startingFen = Optional.empty();
    if (fenPositionButton.isSelected()) {
      String value = fenField.getText() == null ? "" : fenField.getText().trim();
      if (value.isEmpty()) {
        validationLabel.setText("Enter a FEN position or choose the initial position.");
        fenField.requestFocus();
        return;
      }
      startingFen = Optional.of(Fen.of(value));
    }
    EventHandler<StartGameEvent> handler = onStartGame.get();
    if (handler != null) {
      handler.handle(
          new StartGameEvent(
              this,
              new ComputerGameConfiguration(
                  DEFAULT_HUMAN_NAME,
                  color,
                  time.timeControl,
                  startingFen,
                  engine.id(),
                  DEFAULT_ENGINE_THINKING_TIME)));
    }
  }

  private void cancel(ActionEvent event) {
    EventHandler<ActionEvent> handler = onCancel.get();
    if (handler != null) {
      handler.handle(event);
    }
  }

  private Label fieldLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("settings-field-label");
    return label;
  }

  private enum TimePreset {
    FIVE_MINUTES("5 minutes", TimeControl.of(Duration.ofMinutes(5), Duration.ZERO)),
    TEN_MINUTES("10 minutes", TimeControl.of(Duration.ofMinutes(10), Duration.ZERO)),
    FIFTEEN_PLUS_TEN("15 minutes + 10 seconds", TimeControl.fifteenPlusTen()),
    UNLIMITED("Unlimited", TimeControl.unlimited());

    private final String label;
    private final TimeControl timeControl;

    TimePreset(String label, TimeControl timeControl) {
      this.label = label;
      this.timeControl = timeControl;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** JavaFX event carrying the validated game configuration selected in this overlay. */
  public static final class StartGameEvent extends javafx.event.Event {

    private final ComputerGameConfiguration configuration;

    private StartGameEvent(
        HumanVsComputerSetupOverlay source, ComputerGameConfiguration configuration) {
      super(source, NULL_SOURCE_TARGET, javafx.event.Event.ANY);
      this.configuration = configuration;
    }

    public ComputerGameConfiguration configuration() {
      return configuration;
    }
  }
}
