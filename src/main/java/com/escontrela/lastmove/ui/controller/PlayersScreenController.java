package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.player.CreatePlayerCommand;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.player.UpdatePlayerCommand;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.PlayerService;
import com.escontrela.lastmove.domain.player.DuplicatePlayerEmailException;
import com.escontrela.lastmove.domain.player.PlayerId;
import com.escontrela.lastmove.infrastructure.persistence.PersistenceUnavailableException;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.message.MessageBoxButtonMode;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for selecting and maintaining local player profiles. */
@Component
public class PlayersScreenController implements UiScreenController {

    private static final double PHOTO_PREVIEW_RADIUS = 72.0;
    private static final double CARD_PHOTO_RADIUS = 82.0;
    private static final int PLAYERS_PER_ROW = 4;
    private static final double EDITOR_CARD_HEIGHT = 510.0;

    private final UiFlowManager uiFlowManager;
    private final PlayerService playerService;
    private final CurrentUserService currentUserService;
    private final FileChooserFactory fileChooserFactory;
    private final ChessSoundService chessSoundService;

    @FXML private StackPane root;
    @FXML private Label unavailableLabel;
    @FXML private Label noPlayersLabel;
    @FXML private GridPane playerGrid;
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
    @FXML private MessageBox deleteConfirmation;
    @FXML private MessageBox featureBlockedConfirmation;

    private Optional<byte[]> selectedPhoto = Optional.empty();
    private Optional<PlayerSummary> editedPlayer = Optional.empty();

    public PlayersScreenController(@Lazy UiFlowManager uiFlowManager, PlayerService playerService,
            CurrentUserService currentUserService, FileChooserFactory fileChooserFactory,
            ChessSoundService chessSoundService) {
        this.uiFlowManager = uiFlowManager;
        this.playerService = playerService;
        this.currentUserService = currentUserService;
        this.fileChooserFactory = fileChooserFactory;
        this.chessSoundService = chessSoundService;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        chessSoundService.preload();
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
        editorTitleLabel.setText(player.fullName());
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
        List<PlayerSummary> players = playerService.listSelectablePlayers();
        boolean hasPlayers = !players.isEmpty();
        noPlayersLabel.setVisible(!hasPlayers);
        noPlayersLabel.setManaged(!hasPlayers);
        playerGrid.setVisible(hasPlayers);
        playerGrid.setManaged(hasPlayers);
        Optional<PlayerId> selectedId = currentUserService.selectedPlayerId();
        for (int index = 0; index < players.size(); index++) {
            PlayerSummary player = players.get(index);
            boolean selected = selectedId.map(player.id()::equals).orElse(false);
            playerGrid.add(createPlayerCard(player, selected), index % PLAYERS_PER_ROW, index / PLAYERS_PER_ROW);
        }
    }

    private VBox createPlayerCard(PlayerSummary player, boolean selected) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(250);
        card.setMinWidth(220);
        card.getStyleClass().add("player-profile-card");
        if (selected) card.getStyleClass().add("player-profile-card-active");

        StackPane portrait = new StackPane();
        portrait.getStyleClass().add("player-portrait-ring");
        portrait.setMinSize(CARD_PHOTO_RADIUS * 2 + 14, CARD_PHOTO_RADIUS * 2 + 14);
        portrait.setPrefSize(CARD_PHOTO_RADIUS * 2 + 14, CARD_PHOTO_RADIUS * 2 + 14);
        portrait.setMaxSize(CARD_PHOTO_RADIUS * 2 + 14, CARD_PHOTO_RADIUS * 2 + 14);
        ImageView photoView = new ImageView();
        photoView.setFitHeight(CARD_PHOTO_RADIUS * 2);
        photoView.setFitWidth(CARD_PHOTO_RADIUS * 2);
        photoView.setPreserveRatio(false);
        photoView.getStyleClass().add("player-card-photo");
        photoView.setClip(new Circle(CARD_PHOTO_RADIUS, CARD_PHOTO_RADIUS, CARD_PHOTO_RADIUS));
        player.photo().ifPresent(bytes -> photoView.setImage(new Image(new ByteArrayInputStream(bytes))));
        Label initials = new Label(playerInitials(player));
        initials.getStyleClass().add("player-card-initials");
        initials.setVisible(player.photo().isEmpty());
        initials.setManaged(player.photo().isEmpty());
        portrait.getChildren().addAll(initials, photoView);
        photoView.setVisible(player.photo().isPresent());
        photoView.setManaged(player.photo().isPresent());

        Label name = new Label(player.fullName()); name.getStyleClass().add("player-card-name");
        Label email = new Label(player.email()); email.getStyleClass().add("player-card-email");
        Label active = new Label(player.systemPlayer() ? "SYSTEM" : "ACTIVE"); active.getStyleClass().add("player-profile-status");
        active.setVisible(selected || player.systemPlayer()); active.setManaged(selected || player.systemPlayer());

        HBox actions = new HBox(14);
        actions.setAlignment(Pos.CENTER);
        if (!player.systemPlayer()) {
            actions.getChildren().addAll(
                    actionButton("Edit profile", "/images/edit_35dp_000000.png", "/images/edit_35dp_FFFFFF.png", () -> editPlayer(player)),
                    actionButton("Delete profile", "/images/delete_35dp_000000.png", "/images/delete_35dp_FFFFFF.png", () -> confirmDelete(player, selected)),
                    actionButton(selected ? "Active player" : "Set as active player", "/images/play_arrow_35dp_000000.png", "/images/play_arrow_35dp_FFFFFF.png", () -> activatePlayer(player), selected));
            portrait.setOnMouseClicked(event -> editPlayer(player));
            name.setOnMouseClicked(event -> editPlayer(player));
        }
        card.getChildren().addAll(portrait, active, name, email, actions);
        return card;
    }

    private com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton actionButton(
            String accessibleText, String lightIcon, String darkIcon, Runnable action) {
        return actionButton(accessibleText, lightIcon, darkIcon, action, false);
    }

    private com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton actionButton(
            String accessibleText, String lightIcon, String darkIcon, Runnable action, boolean selected) {
        var button = new com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton();
        button.getStyleClass().add("player-action-button");
        button.setAccessibleText(accessibleText);
        button.setTooltipText(accessibleText);
        button.setLightIconResource(lightIcon);
        button.setDarkIconResource(darkIcon);
        button.setSelected(selected);
        button.setDisable(selected);
        button.setOnAction(event -> action.run());
        return button;
    }

    private String playerInitials(PlayerSummary player) {
        String first = player.firstName().isBlank() ? "" : player.firstName().substring(0, 1);
        String last = player.lastName().isBlank() ? "" : player.lastName().substring(0, 1);
        String initials = (first + last).toUpperCase();
        return initials.isBlank() ? "?" : initials;
    }

    private void activatePlayer(PlayerSummary player) {
        currentUserService.selectPlayer(player.id());
        chessSoundService.play(ChessSound.MOVE_SELF);
        uiFlowManager.refreshCurrentUserHeader();
        loadPlayers();
    }

    private void confirmDelete(PlayerSummary player, boolean selected) {
        deleteConfirmation.setTitle("Delete player?");
        deleteConfirmation.setMessage(
                selected
                        ? "\"" + player.fullName() + "\" is the active player. Deleting this profile will remove all its information and clear the active player. This cannot be undone."
                        : "Deleting \"" + player.fullName() + "\" will permanently remove all information associated with this profile. This cannot be undone.");
        deleteConfirmation.setAcceptText("Delete player");
        deleteConfirmation.setCancelText("Keep player");
        deleteConfirmation.setOnAccept(event -> showDeletionBlockedMessage());
        deleteConfirmation.show();
    }

    private void showDeletionBlockedMessage() {
        featureBlockedConfirmation.setTitle("Feature unavailable");
        featureBlockedConfirmation.setMessage("Deleting player profiles is currently blocked. No information has been removed.");
        featureBlockedConfirmation.setAcceptText("OK");
        featureBlockedConfirmation.setCancelText("");
        featureBlockedConfirmation.setButtonMode(MessageBoxButtonMode.ACCEPT);
        featureBlockedConfirmation.show();
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
