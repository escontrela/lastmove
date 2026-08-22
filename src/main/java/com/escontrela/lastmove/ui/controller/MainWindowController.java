package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.notification.GameNotificationRepository;
import com.escontrela.lastmove.application.notification.GameNotification;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.ui.component.notification.NotificationsPanel;
import com.escontrela.lastmove.ui.event.ToggleNotificationsPanelEvent;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.ResumeComputerGameEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.application.service.CurrentUserService.ActivePlayerStatus;
import com.escontrela.lastmove.ui.component.profile.CurrentUserAvatarControl;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the LastMove landing screen. */
@Component
public class MainWindowController implements UiScreenController {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
    private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
    private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final UiFlowManager uiFlowManager;
    private final ChessSoundService chessSoundService;
    private final CurrentUserService currentUserService;
    private final GameNotificationRepository notifications;
    private final SavedGameRepository savedGames;
    private final AnalysisSessionService analysisSessionService;
    private final UiEventBus uiEventBus;

    @FXML
    private AnchorPane root;
    @FXML
    private Label featureStatusLabel;
    @FXML
    private ImageView statusBrandLogo;
    @FXML
    private ImageView pgnToolIcon;
    @FXML
    private ImageView fenToolIcon;
    @FXML
    private Button studiesToolButton;
    @FXML
    private Button tacticsToolButton;
    @FXML
    private ImageView localGameToolIcon;
    @FXML
    private ImageView openingToolIcon;
    @FXML
    private ImageView trainingToolIcon;
    @FXML
    private ImageView engineToolIcon;
    @FXML
    private MessageBox startupMessageBox;
    @FXML
    private ContextualMenuPanel contextualMenuPanel;
    @FXML
    private CurrentUserAvatarControl currentUserAvatar;
    @FXML private NotificationsPanel notificationsPanel;

    private final ListChangeListener<String> themeStyleListener = change -> updateThemeAssets();
    private boolean startupMessageShown;

    public MainWindowController(
            @Lazy UiFlowManager uiFlowManager,
            ChessSoundService chessSoundService,
            CurrentUserService currentUserService, GameNotificationRepository notifications, SavedGameRepository savedGames,
            AnalysisSessionService analysisSessionService, UiEventBus uiEventBus) {
        this.uiFlowManager = uiFlowManager;
        this.chessSoundService = chessSoundService;
        this.currentUserService = currentUserService;
        this.notifications = notifications;
        this.savedGames = savedGames;
        this.analysisSessionService = analysisSessionService;
        this.uiEventBus = uiEventBus;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        chessSoundService.preload();
        root.getStyleClass().addListener(themeStyleListener);
        updateThemeAssets();
        updateCurrentUserAvatar();
        configureContextMenu();
        notificationsPanel.setOnOpen(this::openNotificationGame);
        notificationsPanel.setOnDelete(notification -> { currentUserService.selectedPlayerId().ifPresent(owner -> notifications.deleteById(owner, notification.id())); refreshNotifications(); });
        notificationsPanel.setOnClear(() -> { currentUserService.selectedPlayerId().ifPresent(notifications::deleteAll); refreshNotifications(); });
        startupMessageBox.setOnAccept(event -> openPgnAnalysis());
        startupMessageBox.setOnCancel(event ->
                featureStatusLabel.setText("Welcome to LastMove Chess."));
        startupMessageBox.setOnClose(event ->
                featureStatusLabel.setText("Welcome to LastMove Chess."));
    }

    @Override
    public void onShow() {
        updateCurrentUserAvatar();
        updateStudiesAvailability();
        refreshNotifications();
        if (!startupMessageShown) {
            startupMessageShown = true;
            chessSoundService.play(ChessSound.NOTIFY);
            startupMessageBox.show();
        }
    }

    @FXML
    public void openPgnAnalysis() {
        uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
    }

    /** Opens the transient board-authoring workspace. */
    @FXML
    public void openPositionEditor() {
        uiFlowManager.show(UiScreenId.POSITION_EDITOR);
    }

    /** Opens the persistent study library for the selected local player. */
    @FXML
    public void openStudies() {
        if (currentUserService.activePlayerState().status() != ActivePlayerStatus.ACTIVE) {
            featureStatusLabel.setText("Select an active player profile before opening studies.");
            return;
        }
        uiFlowManager.show(UiScreenId.STUDIES);
    }

    /** Opens the local tactic-suite library. */
    @FXML
    public void openTactics() {
        if (currentUserService.activePlayerState().status() != ActivePlayerStatus.ACTIVE) {
            featureStatusLabel.setText("Select an active player profile before opening tactics.");
            return;
        }
        uiFlowManager.show(UiScreenId.TACTICS);
    }

    /** Opens the progressive-game screen, which owns its Human vs Computer setup overlay. */
    @FXML
    public void openHumanVsComputer() {
        uiFlowManager.show(UiScreenId.HUMAN_VS_COMPUTER);
    }

    @FXML
    public void openMyGames() {
        if (currentUserService.activePlayerState().status() != ActivePlayerStatus.ACTIVE) {
            featureStatusLabel.setText("Select an active player profile before opening your games.");
            return;
        }
        uiFlowManager.show(UiScreenId.MY_GAMES);
    }

    @org.springframework.context.event.EventListener
    public void toggleNotifications(ToggleNotificationsPanelEvent event) {
        if (notificationsPanel.isShowing()) { notificationsPanel.hide(); return; }
        refreshNotifications(); notificationsPanel.show();
    }
    private void refreshNotifications() {
        currentUserService.selectedPlayerId().ifPresentOrElse(owner -> {
            Map<com.escontrela.lastmove.domain.game.GameId, SavedGameSummary> summaries = savedGames.listSummaries(owner).stream().collect(java.util.stream.Collectors.toMap(SavedGameSummary::gameId, summary -> summary));
            notificationsPanel.setNotifications(notifications.findByOwner(owner).stream().map(notification -> notificationEntry(notification, summaries.get(notification.gameId()))).toList());
        }, () -> notificationsPanel.setNotifications(java.util.List.of()));
    }
    private NotificationsPanel.NotificationEntry notificationEntry(GameNotification notification, SavedGameSummary game) {
        if (game == null) return new NotificationsPanel.NotificationEntry(notification, "Saved game", "Game no longer available", "Open");
        String players = game.whiteName() + " vs " + game.blackName();
        String outcome = game.finished() ? game.result().map(this::resultLabel).orElse("Finished") : "In progress";
        boolean resumable = !game.finished() && currentUserService.selectedPlayerId()
            .flatMap(owner -> savedGames.findSaved(notification.gameId()).flatMap(saved -> saved.context().ownerPlayerId()).filter(owner::equals)).isPresent();
        return new NotificationsPanel.NotificationEntry(notification, players, outcome, resumable ? "Resume" : "Open");
    }
    private void openNotificationGame(GameNotification notification) {
        var saved = savedGames.findSaved(notification.gameId()).orElse(null);
        if (saved == null) { refreshNotifications(); return; }
        boolean resumable = saved.game().result().isEmpty() && currentUserService.selectedPlayerId()
            .flatMap(owner -> saved.context().ownerPlayerId().filter(owner::equals)).isPresent();
        notificationsPanel.hide();
        if (resumable) {
            uiFlowManager.show(UiScreenId.HUMAN_VS_COMPUTER);
            uiEventBus.publish(new ResumeComputerGameEvent(notification.gameId()));
            return;
        }
        var session = analysisSessionService.createFromGame(saved.game().toRecord());
        uiEventBus.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Opened game from notification"));
        uiFlowManager.show(UiScreenId.PGN_ANALYSIS);
    }
    private String resultLabel(com.escontrela.lastmove.domain.game.GameResult result) {
        return switch (result) { case WHITE_WINS -> "White wins"; case BLACK_WINS -> "Black wins"; case DRAW -> "Draw"; case UNKNOWN -> "Result unknown"; };
    }

    @FXML
    public void openSetup() {
        uiFlowManager.show(UiScreenId.SETUP);
    }

    @FXML
    public void openPlayers() {
        uiFlowManager.show(UiScreenId.PLAYERS);
    }

    @FXML
    public void showComingSoon(ActionEvent event) {
        String featureName = ((Button) event.getSource()).getAccessibleText();
        featureStatusLabel.setText(featureName + " is coming soon.");
    }

    @FXML
    public void showContextMenu(ContextMenuEvent event) {
        contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
        event.consume();
    }

    private void configureContextMenu() {
        contextualMenuPanel.clearItems();
        contextualMenuPanel.addItem("Analyse a PGN", "", event -> openPgnAnalysis());
        contextualMenuPanel.addItem("Position editor", "", event -> openPositionEditor());
        if (currentUserService.activePlayerState().status() == ActivePlayerStatus.ACTIVE) {
            contextualMenuPanel.addItem("My studies", "", event -> openStudies());
            contextualMenuPanel.addItem("My games", "", event -> openMyGames());
            contextualMenuPanel.addItem("Tactic suites", "", event -> openTactics());
        }
        contextualMenuPanel.addItem("Human vs computer", "", event -> openHumanVsComputer());
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("Players", "", event -> openPlayers());
        contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
        contextualMenuPanel.addItem("Dismiss welcome message", "Esc", event -> {
            startupMessageBox.hide();
            featureStatusLabel.setText("Welcome message dismissed.");
        });
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("About LastMove Chess", "", event ->
                featureStatusLabel.setText("LastMove Chess — your chess study workspace."));
    }

    private void updateStatusBrandLogo() {
        String resource = isNightMode()
                ? DARK_LOGO_RESOURCE
                : LIGHT_LOGO_RESOURCE;
        statusBrandLogo.setImage(loadImage(resource));
    }

    private void updateThemeAssets() {
        updateStatusBrandLogo();
        String iconColor = isNightMode() ? "FFFFFF" : "000000";
        updateToolIcon(pgnToolIcon, "history", iconColor);
        updateToolIcon(fenToolIcon, "structure", iconColor);
        updateToolIcon(localGameToolIcon, "swords", iconColor);
        updateToolIcon(openingToolIcon, "search", iconColor);
        updateToolIcon(trainingToolIcon, "filter", iconColor);
        updateToolIcon(engineToolIcon, "zoom", iconColor);
    }

    private void updateCurrentUserAvatar() {
        currentUserAvatar.setDisplayName(currentUserService.currentUser().name());
    }

    private void updateStudiesAvailability() {
        boolean enabled = currentUserService.activePlayerState().status() == ActivePlayerStatus.ACTIVE;
        studiesToolButton.setDisable(!enabled);
        tacticsToolButton.setDisable(!enabled);
        if (!enabled) {
            studiesToolButton.setAccessibleHelp("Select an active player profile to use persistent studies.");
        } else {
            studiesToolButton.setAccessibleHelp(null);
        }
    }

    private boolean isNightMode() {
        return root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS);
    }

    private void updateToolIcon(ImageView imageView, String iconName, String iconColor) {
        imageView.setImage(loadImage("/images/" + iconName + "_35dp_" + iconColor + ".png"));
    }

    private Image loadImage(String resource) {
        return IMAGE_CACHE.computeIfAbsent(resource, path -> new Image(Objects.requireNonNull(
                getClass().getResource(path),
                () -> "Missing image resource: " + path).toExternalForm()));
    }
}
