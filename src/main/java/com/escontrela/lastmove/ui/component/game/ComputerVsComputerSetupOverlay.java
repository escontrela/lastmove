package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.application.computer.*;
import com.escontrela.lastmove.domain.game.TimeControl;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.event.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

/** Setup overlay for a non-persisted engine match. */
public final class ComputerVsComputerSetupOverlay extends StackPane {
  private final ComboBox<ComputerEngineDescriptor> white = selector();
  private final ComboBox<ComputerEngineDescriptor> black = selector();
  private final ComboBox<TimePreset> time = new ComboBox<>();
  private final ComboBox<MoveDelayPreset> moveDelay = new ComboBox<>();
  private final Button cancel = new Button("Cancel"), start = new Button("Start game");
  private final Label validation = new Label();
  private final ObjectProperty<EventHandler<StartGameEvent>> onStartGame = new SimpleObjectProperty<>(this, "onStartGame");
  private final ObjectProperty<EventHandler<ActionEvent>> onCancel = new SimpleObjectProperty<>(this, "onCancel");
  private Function<String, Duration> thinkingTime = id -> Duration.ofMillis(500);
  public ComputerVsComputerSetupOverlay() {
    getStyleClass().addAll("message-box-overlay", "computer-game-setup-overlay"); setAlignment(Pos.CENTER); setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    Label eyebrow = label("New engine match", "eyebrow-label"); Label title = label("Computer vs computer", "computer-game-setup-title");
    Label description = label("Choose the engine for each side and the clock. This game stays only in memory.", "computer-game-setup-description"); description.setWrapText(true);
    time.setItems(FXCollections.observableArrayList(TimePreset.values())); time.getSelectionModel().select(TimePreset.TEN_MINUTES); time.setMaxWidth(Double.MAX_VALUE); time.getStyleClass().add("computer-game-setup-combo");
    moveDelay.setItems(FXCollections.observableArrayList(MoveDelayPreset.values())); moveDelay.getSelectionModel().select(MoveDelayPreset.NONE); moveDelay.setMaxWidth(Double.MAX_VALUE); moveDelay.getStyleClass().add("computer-game-setup-combo");
    validation.setWrapText(true); validation.getStyleClass().add("computer-game-setup-validation");
    cancel.getStyleClass().add("secondary-button"); start.getStyleClass().add("primary-button"); start.setDefaultButton(true); cancel.setCancelButton(true);
    Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    VBox card = new VBox(12, eyebrow, title, description, field("White engine"), white, field("Black engine"), black, field("Time control"), time, field("Delay between moves"), moveDelay, validation, new HBox(10, spacer, cancel, start));
    card.setPadding(new Insets(28)); card.setMaxWidth(520); card.setMinHeight(Region.USE_PREF_SIZE); card.setMaxHeight(Region.USE_PREF_SIZE); card.getStyleClass().add("computer-game-setup-card"); getChildren().add(card); setVisible(false); setManaged(false);
    visibleProperty().addListener((o, old, visible) -> setManaged(visible)); cancel.setOnAction(this::cancel); start.setOnAction(this::start);
  }
  public void show(List<ComputerEngineDescriptor> engines, Function<String, Duration> thinkingTime) {
    this.thinkingTime = Objects.requireNonNull(thinkingTime); var values = FXCollections.observableArrayList(engines); white.setItems(values); black.setItems(FXCollections.observableArrayList(engines)); white.getSelectionModel().selectFirst(); black.getSelectionModel().select(engines.size() > 1 ? 1 : 0); time.getSelectionModel().select(TimePreset.TEN_MINUTES); moveDelay.getSelectionModel().select(MoveDelayPreset.NONE); setBusy(false); validation.setText(engines.isEmpty() ? "No computer engine is configured." : ""); start.setDisable(engines.isEmpty()); setVisible(true); toFront(); Platform.runLater(white::requestFocus);
  }
  public void hide() { setVisible(false); setManaged(false); }
  public void setBusy(boolean busy) { white.setDisable(busy); black.setDisable(busy); time.setDisable(busy); moveDelay.setDisable(busy); cancel.setDisable(busy); start.setDisable(busy || white.getItems().isEmpty()); start.setText(busy ? "Starting…" : "Start game"); if (busy) validation.setText("Starting the computer engines…"); }
  public void showError(String message) { setBusy(false); validation.setText(Objects.requireNonNullElse(message, "Unable to start the game")); }
  public void setOnStartGame(EventHandler<StartGameEvent> value) { onStartGame.set(value); } public void setOnCancel(EventHandler<ActionEvent> value) { onCancel.set(value); }
  private void start(ActionEvent ignored) { if (white.getValue() == null || black.getValue() == null || time.getValue() == null || moveDelay.getValue() == null) { validation.setText("Choose both engines, a time control and a delay."); return; } var handler=onStartGame.get(); if (handler != null) handler.handle(new StartGameEvent(this, new ComputerVsComputerConfiguration(white.getValue().id(), black.getValue().id(), time.getValue().control, min(thinkingTime.apply(white.getValue().id()), thinkingTime.apply(black.getValue().id())), moveDelay.getValue().duration))); }
  private void cancel(ActionEvent event) { if (onCancel.get()!=null) onCancel.get().handle(event); }
  private static Duration min(Duration a, Duration b) { return a.compareTo(b) <= 0 ? a : b; }
  private static ComboBox<ComputerEngineDescriptor> selector() { ComboBox<ComputerEngineDescriptor> box = new ComboBox<>(); box.setMaxWidth(Double.MAX_VALUE); box.getStyleClass().add("computer-game-setup-combo"); box.setConverter(new StringConverter<>() { public String toString(ComputerEngineDescriptor d) { return d == null ? "" : d.displayName()+" "+d.version(); } public ComputerEngineDescriptor fromString(String text) { throw new UnsupportedOperationException(); }}); return box; }
  private static Label field(String text) { return label(text, "settings-field-label"); } private static Label label(String text, String style) { Label label=new Label(text); label.getStyleClass().add(style); return label; }
  private enum TimePreset { FIVE_MINUTES("5 minutes", TimeControl.of(Duration.ofMinutes(5), Duration.ZERO)), TEN_MINUTES("10 minutes", TimeControl.of(Duration.ofMinutes(10), Duration.ZERO)), FIFTEEN_PLUS_TEN("15 minutes + 10 seconds", TimeControl.fifteenPlusTen()), UNLIMITED("Unlimited", TimeControl.unlimited()); final String text; final TimeControl control; TimePreset(String text, TimeControl control) { this.text=text; this.control=control; } public String toString() { return text; }}
  private enum MoveDelayPreset { NONE("No delay", Duration.ZERO), QUARTER_SECOND("0.25 seconds", Duration.ofMillis(250)), HALF_SECOND("0.5 seconds", Duration.ofMillis(500)), ONE_SECOND("1 second", Duration.ofSeconds(1)), TWO_SECONDS("2 seconds", Duration.ofSeconds(2)); final String text; final Duration duration; MoveDelayPreset(String text, Duration duration) { this.text=text; this.duration=duration; } public String toString() { return text; }}
  public static final class StartGameEvent extends Event { private final ComputerVsComputerConfiguration configuration; StartGameEvent(ComputerVsComputerSetupOverlay source, ComputerVsComputerConfiguration configuration) { super(source, NULL_SOURCE_TARGET, Event.ANY); this.configuration=configuration; } public ComputerVsComputerConfiguration configuration() { return configuration; }}
}
