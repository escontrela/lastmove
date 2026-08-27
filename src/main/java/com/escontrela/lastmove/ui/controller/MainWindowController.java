package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.notification.GameNotificationRepository;
import com.escontrela.lastmove.application.notification.GameNotification;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.ComputerVsComputerGameService;
import com.escontrela.lastmove.ui.component.notification.NotificationsPanel;
import com.escontrela.lastmove.ui.event.ToggleNotificationsPanelEvent;
import com.escontrela.lastmove.application.event.ComputerGameFinishedEvent;
import com.escontrela.lastmove.application.event.ComputerOpponentMovedEvent;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.ResumeComputerGameEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.application.service.CurrentUserService.ActivePlayerStatus;
import com.escontrela.lastmove.ui.component.message.MessageBox;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ChessSound;
import com.escontrela.lastmove.ui.service.ChessSoundService;
import java.util.List;
import java.util.Map;
import javafx.collections.ListChangeListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the LastMove landing screen. */
@Component
public class MainWindowController implements UiScreenController {

    private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
    private final UiFlowManager uiFlowManager;
    private final ChessSoundService chessSoundService;
    private final CurrentUserService currentUserService;
    private final GameNotificationRepository notifications;
    private final SavedGameRepository savedGames;
    private final AnalysisSessionService analysisSessionService;
    private final UiEventBus uiEventBus;
    private final ComputerVsComputerGameService computerVsComputerGames;

    @FXML
    private AnchorPane root;
    @FXML
    private Label welcomeLabel;
    @FXML
    private Button studiesToolButton;
    @FXML
    private Button tacticsToolButton;
    @FXML
    private VBox recentGamesBox;
    @FXML
    private MessageBox startupMessageBox;
    @FXML
    private ContextualMenuPanel contextualMenuPanel;
    @FXML private NotificationsPanel notificationsPanel;

    private final ListChangeListener<String> themeStyleListener = change -> updateThemeAssets();
    private boolean startupMessageShown;

    public MainWindowController(
            @Lazy UiFlowManager uiFlowManager,
            ChessSoundService chessSoundService,
            CurrentUserService currentUserService, GameNotificationRepository notifications, SavedGameRepository savedGames,
            AnalysisSessionService analysisSessionService, UiEventBus uiEventBus,
            ComputerVsComputerGameService computerVsComputerGames) {
        this.uiFlowManager = uiFlowManager;
        this.chessSoundService = chessSoundService;
        this.currentUserService = currentUserService;
        this.notifications = notifications;
        this.savedGames = savedGames;
        this.analysisSessionService = analysisSessionService;
        this.uiEventBus = uiEventBus;
        this.computerVsComputerGames = computerVsComputerGames;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        chessSoundService.preload();
        root.getStyleClass().addListener(themeStyleListener);
        updateThemeAssets();
        configureContextMenu();
        notificationsPanel.setOnOpen(this::openNotificationGame);
        notificationsPanel.setOnDelete(notification -> { currentUserService.selectedPlayerId().ifPresent(owner -> notifications.deleteById(owner, notification.id())); refreshNotifications(); });
        notificationsPanel.setOnClear(() -> { currentUserService.selectedPlayerId().ifPresent(notifications::deleteAll); refreshNotifications(); });
        notificationsPanel.setComputerMatchAvailable(false, this::openComputerVsComputer);
        startupMessageBox.setOnAccept(event -> openPgnAnalysis());
        startupMessageBox.setOnCancel(event ->
                setFeatureStatus("Welcome to LastMove Chess."));
        startupMessageBox.setOnClose(event ->
                setFeatureStatus("Welcome to LastMove Chess."));
    }

    @Override
    public void onShow() {
        updateWelcomeAndRecentGames();
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
            setFeatureStatus("Select an active player profile before opening studies.");
            return;
        }
        uiFlowManager.show(UiScreenId.STUDIES);
    }

    /** Opens the local tactic-suite library. */
    @FXML
    public void openTactics() {
        if (currentUserService.activePlayerState().status() != ActivePlayerStatus.ACTIVE) {
            setFeatureStatus("Select an active player profile before opening tactics.");
            return;
        }
        uiFlowManager.show(UiScreenId.TACTICS);
    }

    /** Opens the progressive-game screen, which owns its Human vs Computer setup overlay. */
    @FXML
    public void openHumanVsComputer() {
        uiFlowManager.show(UiScreenId.HUMAN_VS_COMPUTER);
    }

    /** Opens a transient match between two configured computer engines. */
    @FXML
    public void openComputerVsComputer() {
        uiFlowManager.show(UiScreenId.COMPUTER_VS_COMPUTER);
    }

    @FXML
    public void openMyGames() {
        if (currentUserService.activePlayerState().status() != ActivePlayerStatus.ACTIVE) {
            setFeatureStatus("Select an active player profile before opening your games.");
            return;
        }
        uiFlowManager.show(UiScreenId.MY_GAMES);
    }

    @org.springframework.context.event.EventListener
    public void toggleNotifications(ToggleNotificationsPanelEvent event) {
        if (notificationsPanel.isShowing()) { notificationsPanel.hide(); return; }
        refreshNotifications(); notificationsPanel.show();
    }
    /** Refreshes in-place notifications when a background game finishes away from its screen. */
    @org.springframework.context.event.EventListener
    public void computerGameFinished(ComputerGameFinishedEvent event) {
        Platform.runLater(this::refreshNotifications);
    }
    @org.springframework.context.event.EventListener
    public void computerOpponentMoved(ComputerOpponentMovedEvent event) {
        Platform.runLater(this::refreshNotifications);
    }
    private void refreshNotifications() {
        notificationsPanel.setComputerMatchAvailable(
                computerVsComputerGames.gamesInMemory().stream()
                        .anyMatch(game -> game.result().isEmpty() && !game.stopped()),
                this::openComputerVsComputer);
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
        return new NotificationsPanel.NotificationEntry(notification, players, outcome, resumable ? "Resume" : "View event");
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
        uiEventBus.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Opened completed game from notification"));
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
        setFeatureStatus(featureName + " is coming soon.");
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
        contextualMenuPanel.addItem("Computer vs computer", "", event -> openComputerVsComputer());
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("Players", "", event -> openPlayers());
        contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
        contextualMenuPanel.addItem("Dismiss welcome message", "Esc", event -> {
            startupMessageBox.hide();
            setFeatureStatus("Welcome message dismissed.");
        });
        contextualMenuPanel.addSeparator();
        contextualMenuPanel.addItem("About LastMove Chess", "", event ->
                setFeatureStatus("LastMove Chess — your chess study workspace."));
    }

    private void updateThemeAssets() {
    }

    /** The home dashboard has no inline status label; user feedback is presented by its overlays. */
    private void setFeatureStatus(String message) {
        // Intentionally empty until the dashboard gains a dedicated non-intrusive feedback surface.
    }

    private void updateWelcomeAndRecentGames() {
        String name = currentUserService.currentUser().name();
        welcomeLabel.setText("unknown".equalsIgnoreCase(name) ? "Welcome back" : "Welcome back, " + name);

        List<SavedGameSummary> recentGames = currentUserService.selectedPlayerId()
                .map(savedGames::listSummaries)
                .orElseGet(List::of)
                .stream()
                .limit(2)
                .toList();
        if (recentGames.isEmpty()) {
            recentGamesBox.getChildren().setAll(recentGameRow("No games yet", "Start a game and it will appear here."));
            return;
        }
        recentGamesBox.getChildren().setAll(recentGames.stream()
                .map(game -> recentGameRow(
                        game.whiteName() + " vs " + game.blackName(),
                        gameOutcome(game) + " · " + game.movesCount() + " moves"))
                .toList());
    }

    private VBox recentGameRow(String title, String summary) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("home-recent-game-title");
        Label summaryLabel = new Label(summary);
        summaryLabel.getStyleClass().add("home-recent-game-summary");
        return new VBox(2, titleLabel, summaryLabel);
    }

    private String gameOutcome(SavedGameSummary game) {
        return game.finished() ? game.result().map(this::resultLabel).orElse("Finished") : "In progress";
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

}
