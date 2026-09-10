package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.ui.component.profile.PlayerAvatarControl;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.util.Objects;
import java.util.Optional;
import javafx.util.Duration;

/** Brief player-versus-player introduction shown when a local game starts. */
public final class GameStartPlayersOverlay extends StackPane {
    private final PlayerAvatarControl whiteAvatar = new PlayerAvatarControl();
    private final PlayerAvatarControl blackAvatar = new PlayerAvatarControl();
    private final Label whiteName = new Label();
    private final Label blackName = new Label();
    private final Button continueButton = new Button("Press to continue  →");
    private final Label countdownLabel = new Label();
    private final FadeTransition fadeIn = new FadeTransition(Duration.millis(220), this);
    private final FadeTransition fadeOut = new FadeTransition(Duration.millis(180), this);
    private final Timeline startCountdown = new Timeline(
        new KeyFrame(Duration.seconds(1), event -> updateCountdown()));
    private Runnable onDismiss = () -> {};
    private Runnable onCancel = () -> {};
    private Runnable afterFade = () -> {};
    private int countdownSeconds;
    private boolean dismissing;

    /** UI-only player presentation for the game introduction. */
    public record PlayerPresentation(
        String name, Optional<byte[]> photo, boolean knightshade, boolean featured) {
      public PlayerPresentation {
        name = Objects.requireNonNullElse(name, "Unknown player");
        photo = Objects.requireNonNullElse(photo, Optional.empty());
      }

      public static PlayerPresentation named(String name) {
        return new PlayerPresentation(name, Optional.empty(), false, false);
      }
    }

    public GameStartPlayersOverlay() {
        getStyleClass().add("game-start-players-overlay");
        setAlignment(Pos.CENTER);
        setFocusTraversable(true);
        setMinSize(USE_PREF_SIZE, USE_PREF_SIZE);
        setPrefSize(560, 410);
        setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        HBox players = new HBox(28, player(whiteAvatar, whiteName), versus(), player(blackAvatar, blackName));
        players.setAlignment(Pos.CENTER);
        Label title = new Label("Game");
        title.getStyleClass().add("game-start-title");
        Label introduction = new Label("Get ready for an exciting match!");
        introduction.getStyleClass().add("game-start-introduction");
        VBox heading = new VBox(4, title, introduction);
        heading.setAlignment(Pos.CENTER);
        continueButton.getStyleClass().add("game-start-continue-button");
        continueButton.setOnAction(event -> dismiss());
        Label afterGame = new Label("The analysis will start after the game.");
        afterGame.getStyleClass().add("game-start-after-game");
        Label cancelHint = new Label("ESC to cancel");
        cancelHint.getStyleClass().add("game-start-cancel-hint");
        countdownLabel.getStyleClass().add("game-start-countdown");
        VBox footer = new VBox(4, afterGame, countdownLabel, cancelHint);
        footer.setAlignment(Pos.CENTER);
        VBox card = new VBox(22, heading, players, continueButton, footer);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("game-start-players-card");
        getChildren().add(card);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> finishDismiss());
        startCountdown.setCycleCount(10);
        startCountdown.setOnFinished(event -> dismiss());
        addEventFilter(
            KeyEvent.KEY_PRESSED,
            event -> {
              if (event.getCode() == KeyCode.ESCAPE) {
                cancel();
                event.consume();
              }
            });
        setVisible(false);
        setManaged(false);
    }

    public void show(String white, String black, Runnable afterDismiss) {
        show(PlayerPresentation.named(white), PlayerPresentation.named(black), afterDismiss, () -> {});
    }

    /** Shows white first and black second, matching the board and game notation order. */
    public void show(
        PlayerPresentation white, PlayerPresentation black, Runnable afterDismiss) {
        show(white, black, afterDismiss, () -> {});
    }

    /** Shows white first and black second, with an optional Escape cancellation action. */
    public void show(
        PlayerPresentation white,
        PlayerPresentation black,
        Runnable afterDismiss,
        Runnable afterCancel) {
        whiteName.setText(white.name());
        blackName.setText(black.name());
        avatar(whiteAvatar, white);
        avatar(blackAvatar, black);
        onDismiss = afterDismiss == null ? () -> {} : afterDismiss;
        onCancel = afterCancel == null ? () -> {} : afterCancel;
        countdownSeconds = 10;
        updateCountdownLabel();
        dismissing = false;
        fadeOut.stop();
        startCountdown.playFromStart();
        setVisible(true);
        setManaged(true);
        setOpacity(0);
        toFront();
        requestFocus();
        fadeIn.playFromStart();
    }

    private VBox player(PlayerAvatarControl avatar, Label name) {
        name.getStyleClass().add("game-start-player-name");
        VBox box = new VBox(8, avatar, name);
        box.setAlignment(Pos.CENTER);
        return box;
    }
    private Label versus() { Label label = new Label("vs"); label.getStyleClass().add("game-start-versus"); return label; }
    private void avatar(PlayerAvatarControl avatar, PlayerPresentation player) {
        avatar.setAvatarSize(128);
        avatar.getStyleClass().remove("game-start-player-avatar-featured");
        if (player.featured()) {
            avatar.getStyleClass().add("game-start-player-avatar-featured");
        }
        player.photo().ifPresentOrElse(
            photo -> avatar.showPhoto(photo, player.name()),
            () -> {
              if (player.knightshade()) avatar.showKnightshade(player.name());
              else avatar.showInitials(player.name());
            });
    }
    private void dismiss() {
        close(onDismiss);
    }

    private void cancel() {
        close(onCancel);
    }

    private void close(Runnable action) {
        if (!isVisible() || dismissing) return;
        dismissing = true;
        startCountdown.stop();
        afterFade = action;
        fadeIn.stop();
        fadeOut.setFromValue(getOpacity());
        fadeOut.playFromStart();
    }

    private void finishDismiss() {
        setVisible(false);
        setManaged(false);
        setOpacity(1);
        dismissing = false;
        afterFade.run();
        afterFade = () -> {};
    }

    private void updateCountdown() {
        if (!isVisible() || dismissing) return;
        countdownSeconds = Math.max(0, countdownSeconds - 1);
        updateCountdownLabel();
    }

    private void updateCountdownLabel() {
        countdownLabel.setText("Starting automatically in " + countdownSeconds + "s");
    }
}
