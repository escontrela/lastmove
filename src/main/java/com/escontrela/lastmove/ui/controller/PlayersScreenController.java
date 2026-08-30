package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.player.CreatePlayerCommand;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.player.UpdatePlayerCommand;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.PlayerService;
import com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for selecting and maintaining local player profiles. */
@Component
public class PlayersScreenController implements UiScreenController {

    private static final double PHOTO_PREVIEW_RADIUS = 40.0;
    private static final double CARD_PHOTO_RADIUS = 26.0;
    private static final double EDITOR_CARD_HEIGHT = 480.0;

    private final UiFlowManager uiFlowManager;
    private final PlayerService playerService;
    private final CurrentUserService currentUserService;
    private final FileChooserFactory fileChooserFactory;

    @FXML private StackPane root;
    @FXML private Label unavailableLabel;
    @FXML private Label noPlayersLabel;
    @FXML private VBox playerGrid;
    @FXML private Button newPlayerButton;
    @FXML private StackPane playerEditorOverlay;
    @FXML private Label editorEyebrowLabel;
    @FXML private Label editorTitleLabel;
    @FXML private TextField emailField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private Button clearPhotoButton;
    @FXML private ImageView photoPreview;
    @FXML private Button saveButton;
    @FXML private Label validationLabel;
    @FXML private VBox playerEditorCard;
    @FXML private ContextualMenuPanel playerContextMenu;
    @FXML private MessageBox deleteConfirmation;

    private Optional<byte[]> selectedPhoto = Optional.empty();
    private Optional<PlayerSummary> editedPlayer = Optional.empty();
    /** The profile targeted by the most recent contextual action, separate from the active player. */
    private Optional<PlayerId> contextSelectedPlayerId = Optional.empty();

    public PlayersScreenController(@Lazy UiFlowManager uiFlowManager, PlayerService playerService,
            CurrentUserService currentUserService, FileChooserFactory fileChooserFactory) {
        this.uiFlowManager = uiFlowManager;
        this.playerService = playerService;
        this.currentUserService = currentUserService;
        this.fileChooserFactory = fileChooserFactory;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        photoPreview.setClip(new Circle(PHOTO_PREVIEW_RADIUS, PHOTO_PREVIEW_RADIUS, PHOTO_PREVIEW_RADIUS));
        playerEditorCard.setPrefHeight(EDITOR_CARD_HEIGHT);
        playerEditorCard.setMaxHeight(EDITOR_CARD_HEIGHT);
        hideEditor();
    }

    @Override
    public void onShow() {
        hideEditor();
        boolean available = playerService.isPersistenceAvailable();
        unavailableLabel.setVisible(!available);
        unavailableLabel.setManaged(!available);
        newPlayerButton.setDisable(!available);
        if (!available) {
            unavailableLabel.setText("Player persistence is unavailable"
                    + playerService.persistenceUnavailableReason().map(reason -> ": " + reason).orElse("")
                    + ". You can still use LastMove, but profiles cannot be changed.");
        }
        loadPlayers();
    }

    @FXML
    public void showNewPlayerEditor() {
        editedPlayer = Optional.empty();
        editorEyebrowLabel.setText("NEW PLAYER");
        editorTitleLabel.setText("Create a player profile");
        saveButton.setText("Create player");
        clearForm();
        showEditor();
    }

    @FXML
    public void choosePhoto() {
        fileChooserFactory.chooseImageFile(root.getScene().getWindow()).ifPresent(this::loadPhoto);
    }

    @FXML
    public void clearPhoto() {
        selectedPhoto = Optional.empty();
        photoPreview.setImage(null);
        photoPreview.setVisible(false);
        photoPreview.setManaged(false);
        clearPhotoButton.setVisible(false);
        clearPhotoButton.setManaged(false);
    }

    @FXML
    public void savePlayer() {
        clearValidation();
        try {
            if (editedPlayer.isPresent()) {
                playerService.updatePlayer(new UpdatePlayerCommand(editedPlayer.orElseThrow().id(), emailField.getText(),
                        firstNameField.getText(), lastNameField.getText(), selectedPhoto));
            } else {
                playerService.createPlayer(new CreatePlayerCommand(emailField.getText(), firstNameField.getText(),
                        lastNameField.getText(), selectedPhoto));
            }
            hideEditor();
            loadPlayers();
        } catch (DuplicatePlayerEmailException | IllegalArgumentException | PersistenceUnavailableException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    public void hideEditor() {
        playerEditorOverlay.setVisible(false);
        playerEditorOverlay.setManaged(false);
    }

    @FXML
    public void backToMain() {
        uiFlowManager.show(UiScreenId.MAIN);
    }

    private void showEditor() {
        playerEditorOverlay.setVisible(true);
        playerEditorOverlay.setManaged(true);
        playerEditorOverlay.toFront();
        Platform.runLater(firstNameField::requestFocus);
    }

    private void editPlayer(PlayerSummary player) {
        editedPlayer = Optional.of(player);
        editorEyebrowLabel.setText("PLAYER PROFILE");
        editorTitleLabel.setText("Edit " + player.fullName());
        saveButton.setText("Save changes");
        emailField.setText(player.email());
        firstNameField.setText(player.firstName());
        lastNameField.setText(player.lastName());
        selectedPhoto = player.photo();
        renderPhotoPreview();
        clearValidation();
        showEditor();
    }

    private void loadPhoto(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (bytes.length == 0 || image.isError()) {
                showError("Selected file is not a valid image.");
                return;
            }
            selectedPhoto = Optional.of(bytes);
            renderPhotoPreview();
        } catch (IOException exception) {
            showError("Unable to read selected photo: " + exception.getMessage());
        }
    }

    private void loadPlayers() {
        playerGrid.getChildren().clear();
        if (!playerService.isPersistenceAvailable()) {
            noPlayersLabel.setVisible(false);
            noPlayersLabel.setManaged(false);
            playerGrid.setVisible(false);
            playerGrid.setManaged(false);
            return;
        }
        List<PlayerSummary> players = playerService.listPlayers();
        boolean hasPlayers = !players.isEmpty();
        noPlayersLabel.setVisible(!hasPlayers);
        noPlayersLabel.setManaged(!hasPlayers);
        playerGrid.setVisible(hasPlayers);
        playerGrid.setManaged(hasPlayers);
        Optional<PlayerId> selectedId = currentUserService.selectedPlayerId();
        for (PlayerSummary player : players) {
            boolean selected = selectedId.map(player.id()::equals).orElse(false);
            playerGrid.getChildren().add(createPlayerRow(player, selected));
        }
    }

    private HBox createPlayerRow(PlayerSummary player, boolean selected) {
        HBox card = new HBox(14);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("player-row");
        if (selected) card.getStyleClass().add("player-row-active");
        if (contextSelectedPlayerId.map(player.id()::equals).orElse(false)) {
            card.getStyleClass().add("player-row-context-selected");
        }
        ImageView photoView = new ImageView();
        photoView.setFitHeight(CARD_PHOTO_RADIUS * 2);
        photoView.setFitWidth(CARD_PHOTO_RADIUS * 2);
        photoView.setPreserveRatio(true);
        photoView.getStyleClass().add("player-card-photo");
        photoView.setClip(new Circle(CARD_PHOTO_RADIUS, CARD_PHOTO_RADIUS, CARD_PHOTO_RADIUS));
        player.photo().ifPresent(bytes -> photoView.setImage(new Image(new ByteArrayInputStream(bytes))));

        VBox details = new VBox(4);
        Label name = new Label(player.fullName()); name.getStyleClass().add("player-card-name");
        Label email = new Label(player.email()); email.getStyleClass().add("player-card-email");
        Label active = new Label(player.systemPlayer() ? "SYSTEM" : selected ? "ACTIVE" : "AVAILABLE"); active.getStyleClass().add("player-row-status");
        active.setVisible(selected || player.systemPlayer()); active.setManaged(selected || player.systemPlayer());
        details.getChildren().addAll(name, email, active);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label actionsHint = new Label(player.systemPlayer() ? "Managed by Arena" : "Right-click for actions");
        actionsHint.getStyleClass().add("player-row-actions-hint");
        card.setOnContextMenuRequested(event -> {
            if (!player.systemPlayer()) { selectContextRow(player); showPlayerActions(player, selected, event.getSceneX(), event.getSceneY()); }
            event.consume();
        });
        card.getChildren().addAll(photoView, details, spacer, active, actionsHint);
        return card;
    }

    private void selectContextRow(PlayerSummary player) {
        contextSelectedPlayerId = Optional.of(player.id());
        loadPlayers();
    }

    private void showPlayerActions(PlayerSummary player, boolean selected, double sceneX, double sceneY) {
        playerContextMenu.clearItems();
        if (!selected) {
            playerContextMenu.addItem("Set as active player", "", event -> activatePlayer(player));
        }
        playerContextMenu.addItem("Edit profile", "", event -> editPlayer(player));
        playerContextMenu.addSeparator();
        playerContextMenu.addItem("Delete player…", "", event -> confirmDelete(player, selected));
        playerContextMenu.showAtScene(sceneX, sceneY);
    }

    private void activatePlayer(PlayerSummary player) {
        currentUserService.selectPlayer(player.id());
        loadPlayers();
    }

    private void confirmDelete(PlayerSummary player, boolean selected) {
        deleteConfirmation.setTitle("Delete player?");
        deleteConfirmation.setMessage(
                selected
                        ? "\"" + player.fullName() + "\" is the active player. Deleting it will clear the active profile. This cannot be undone."
                        : "Delete \"" + player.fullName() + "\"? This cannot be undone.");
        deleteConfirmation.setAcceptText("Delete player");
        deleteConfirmation.setCancelText("Keep player");
        deleteConfirmation.setOnAccept(event -> deletePlayer(player, selected));
        deleteConfirmation.show();
    }

    private void deletePlayer(PlayerSummary player, boolean selected) {
        try {
            playerService.deletePlayer(player.id());
            if (selected) {
                currentUserService.clearSelection();
            }
            loadPlayers();
        } catch (PersistenceUnavailableException exception) {
            showError(exception.getMessage());
        }
    }

    private void clearForm() {
        emailField.clear(); firstNameField.clear(); lastNameField.clear(); clearPhoto(); clearValidation();
    }

    private void renderPhotoPreview() {
        selectedPhoto.ifPresent(bytes -> photoPreview.setImage(new Image(new ByteArrayInputStream(bytes))));
        boolean hasPhoto = selectedPhoto.isPresent();
        photoPreview.setVisible(hasPhoto); photoPreview.setManaged(hasPhoto);
        clearPhotoButton.setVisible(hasPhoto); clearPhotoButton.setManaged(hasPhoto);
    }

    private void clearValidation() {
        validationLabel.setText("");
        validationLabel.getStyleClass().removeAll("settings-validation-error", "settings-validation-success");
    }

    private void showError(String message) {
        validationLabel.setText(message);
        validationLabel.getStyleClass().add("settings-validation-error");
    }
}
