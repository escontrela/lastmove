package com.escontrela.lastmove.ui.component.arena;

import com.escontrela.lastmove.application.arena.BotChallengeConfiguration;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** In-screen editor for the run settings of autonomous online-bot challenges. */
public final class BotChallengeSettingsModal extends StackPane {
  private final TextField baseMinutes = numericField();
  private final TextField incrementSeconds = numericField();
  private final TextField minimumRating = numericField();
  private final TextField maximumRating = numericField();
  private final TextField maximumGames = numericField();
  private final CheckBox rated = new CheckBox("Rated challenges");
  private final CheckBox repeat = new CheckBox("Repeat only when candidates are exhausted");
  private final Label validation = new Label();
  private Consumer<BotChallengeConfiguration> onSave;

  public BotChallengeSettingsModal() {
    getStyleClass().addAll("message-box-overlay", "bot-challenge-settings-modal");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setFocusTraversable(true);
    setOnKeyPressed(this::handleKey);

    Label title = new Label("Challenge settings");
    title.getStyleClass().add("message-box-title");
    Button close = new Button("×");
    close.getStyleClass().add("message-box-close-button");
    close.setOnAction(event -> hide());
    Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(12, title, spacer, close); header.setAlignment(Pos.CENTER_LEFT);

    GridPane fields = new GridPane();
    fields.setHgap(10); fields.setVgap(8);
    fields.add(fieldLabel("Base time (minutes)"), 0, 0); fields.add(baseMinutes, 1, 0);
    fields.add(fieldLabel("Increment (seconds)"), 2, 0); fields.add(incrementSeconds, 3, 0);
    fields.add(fieldLabel("Min opponent rating"), 0, 1); fields.add(minimumRating, 1, 1);
    fields.add(fieldLabel("Max opponent rating"), 2, 1); fields.add(maximumRating, 3, 1);
    fields.add(fieldLabel("Maximum games"), 0, 2); fields.add(maximumGames, 1, 2);
    GridPane.setHgrow(baseMinutes, Priority.ALWAYS); GridPane.setHgrow(incrementSeconds, Priority.ALWAYS);
    GridPane.setHgrow(minimumRating, Priority.ALWAYS); GridPane.setHgrow(maximumRating, Priority.ALWAYS); GridPane.setHgrow(maximumGames, Priority.ALWAYS);
    rated.getStyleClass().add("settings-check-box"); repeat.getStyleClass().add("settings-check-box");
    HBox options = new HBox(16, rated, repeat); options.setAlignment(Pos.CENTER_LEFT);
    validation.getStyleClass().add("text-input-modal-validation"); validation.setWrapText(true);
    Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("message-box-button", "message-box-cancel-button"); cancel.setCancelButton(true); cancel.setOnAction(event -> hide());
    Button save = new Button("Save settings"); save.getStyleClass().addAll("message-box-button", "message-box-confirm-button"); save.setDefaultButton(true); save.setOnAction(event -> save());
    Region actionSpacer = new Region(); HBox.setHgrow(actionSpacer, Priority.ALWAYS);
    HBox actions = new HBox(10, cancel, actionSpacer, save); actions.setAlignment(Pos.CENTER_RIGHT); actions.getStyleClass().add("message-box-actions");
    VBox card = new VBox(10, header, fields, options, validation, actions);
    card.setPadding(new Insets(16));
    card.setMinHeight(Region.USE_PREF_SIZE); card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setPrefWidth(720); card.setMinWidth(Region.USE_PREF_SIZE); card.setMaxWidth(720);
    card.getStyleClass().addAll("message-box-card", "bot-challenge-settings-modal-card");
    getChildren().add(card); setVisible(false); setManaged(false);
  }

  public void show(BotChallengeConfiguration configuration) {
    baseMinutes.setText(Integer.toString(configuration.clockLimitSeconds() / 60));
    incrementSeconds.setText(Integer.toString(configuration.clockIncrementSeconds()));
    minimumRating.setText(Integer.toString(configuration.minimumOpponentRating()));
    maximumRating.setText(Integer.toString(configuration.maximumOpponentRating()));
    maximumGames.setText(Integer.toString(configuration.maximumGames()));
    rated.setSelected(configuration.rated()); repeat.setSelected(configuration.allowRepeatWhenExhausted());
    validation.setText(""); setManaged(true); setVisible(true); toFront(); Platform.runLater(baseMinutes::requestFocus);
  }

  public void hide() { setVisible(false); setManaged(false); }
  public void setOnSave(Consumer<BotChallengeConfiguration> handler) { onSave = handler; }

  private void save() {
    try {
      BotChallengeConfiguration configuration = new BotChallengeConfiguration(value(baseMinutes) * 60, value(incrementSeconds), "standard", rated.isSelected(), value(minimumRating), value(maximumRating), value(maximumGames), true, repeat.isSelected());
      if (onSave != null) onSave.accept(configuration);
      hide();
    } catch (IllegalArgumentException exception) { validation.setText(exception.getMessage()); }
  }

  private void handleKey(KeyEvent event) { if (event.getCode() == KeyCode.ESCAPE) { hide(); event.consume(); } }
  private static Label fieldLabel(String text) { Label label = new Label(text); label.getStyleClass().add("settings-field-label"); return label; }
  private static TextField numericField() { TextField field = new TextField(); field.getStyleClass().add("text-input-modal-field"); field.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change -> change.getControlNewText().matches("\\d*") ? change : null)); return field; }
  private static int value(TextField field) { if (field.getText().isBlank()) throw new IllegalArgumentException("Complete every numeric setting."); try { return Integer.parseInt(field.getText()); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Each setting must be a whole number."); } }
}
