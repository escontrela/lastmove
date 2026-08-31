package com.escontrela.lastmove.ui.screen;

import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.notification.GameNotificationRepository;
import com.escontrela.lastmove.ui.component.header.ApplicationHeader;
import com.escontrela.lastmove.ui.component.header.HeaderAction;
import com.escontrela.lastmove.ui.component.header.HeaderBreadcrumb;
import com.escontrela.lastmove.ui.component.header.HeaderConfiguration;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.event.ToggleNotificationsPanelEvent;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import java.util.List;
import java.util.Optional;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** Coordinates navigation between views hosted by the single primary window. */
public class UiFlowManager {

    private final UiScreenFactory screenFactory;
    private final ApplicationThemeService themeService;
    private final CurrentUserService currentUserService;
    private final GameNotificationRepository notifications;
    private final UiEventBus eventBus;
    private UiScreen currentScreen;
    private boolean escapeHandlerInstalled;

    public UiFlowManager(
            UiScreenFactory screenFactory,
            ApplicationThemeService themeService,
            CurrentUserService currentUserService, GameNotificationRepository notifications, UiEventBus eventBus) {
        this.screenFactory = screenFactory;
        this.themeService = themeService;
        this.currentUserService = currentUserService;
        this.notifications = notifications;
        this.eventBus = eventBus;
    }

    public void show(UiScreenId screenId) {
        UiScreen nextScreen = screenFactory.create(screenId);
        if (currentScreen != null) {
            currentScreen.controller().onHide();
        }
        configureHeader(nextScreen.scene().getRoot(), screenId);
        installEscapeShortcut(nextScreen.scene());
        nextScreen.show();
        currentScreen = nextScreen;
    }

    /** Returns the active primary-window view, if one has already been shown. */
    public Optional<UiScreen> currentScreen() {
        return Optional.ofNullable(currentScreen);
    }

    /** Registers the ESC shortcut that returns from any secondary screen to the main window. */
    private void installEscapeShortcut(Scene scene) {
        if (escapeHandlerInstalled) {
            return;
        }
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE
                    && currentScreen != null
                    && currentScreen.id() != UiScreenId.MAIN) {
                show(UiScreenId.MAIN);
            }
        });
        escapeHandlerInstalled = true;
    }

    private void configureHeader(Parent root, UiScreenId screenId) {
        ApplicationHeader header = (ApplicationHeader) root.lookup(".application-header");
        if (header == null) {
            return;
        }
        boolean home = screenId == UiScreenId.MAIN;
        header.configure(HeaderConfiguration.builder()
                .showBackButton(!home)
                .onBack(event -> show(UiScreenId.MAIN))
                .breadcrumbs(breadcrumbsFor(screenId))
                .showStatistics(home)
                .onStatistics(event -> show(UiScreenId.GAME_STATISTICS))
                .showThemeToggle(home)
                .onThemeToggle(event -> themeService.setNightMode(!themeService.currentThemeMode().isNightMode()))
                .contextActions(home ? List.of(new HeaderAction(
                        "Open game notifications",
                        "Notifications",
                        currentUserService.selectedPlayerId().filter(notifications::hasUnread).isPresent() ? "/images/notification_sound_35dp_000000.png" : "/images/notifications_35dp_000000.png",
                        currentUserService.selectedPlayerId().filter(notifications::hasUnread).isPresent() ? "/images/notification_sound_35dp_FFFFFF.png" : "/images/notifications_35dp_FFFFFF.png",
                        event -> eventBus.publish(new ToggleNotificationsPanelEvent()),
                        false), new HeaderAction(
                        "Open Knightshade Arena",
                        "Knightshade Arena",
                        "/images/chess_king_2_35dp_000000.png",
                        "/images/chess_king_2_35dp_FFFFFF.png",
                        event -> show(UiScreenId.KNIGHTSHADE_ARENA),
                        false), new HeaderAction(
                        "Open settings",
                        "Settings",
                        "/images/settings_35dp_000000.png",
                        "/images/settings_35dp_FFFFFF.png",
                        event -> show(UiScreenId.SETUP),
                        false)) : List.of())
                .onAvatar(event -> show(UiScreenId.PLAYERS))
                .currentUserName(currentUserService.currentUser().name())
                .build());
    }

    private List<HeaderBreadcrumb> breadcrumbsFor(UiScreenId screenId) {
        if (screenId == UiScreenId.MAIN) {
            return List.of();
        }
        return List.of(
                HeaderBreadcrumb.link("Home", event -> show(UiScreenId.MAIN)),
                HeaderBreadcrumb.current(titleFor(screenId)));
    }

    private String titleFor(UiScreenId screenId) {
        return switch (screenId) {
            case HUMAN_VS_COMPUTER -> "Human vs Computer";
            case COMPUTER_VS_COMPUTER -> "Computer vs Computer";
            case PGN_ANALYSIS -> "PGN Analysis";
            case POSITION_EDITOR -> "Position Editor";
            case ANALYSIS_SESSIONS -> "Analysis Sessions";
            case STUDIES -> "My Studies";
            case TACTICS -> "Tactic Suites";
            case TACTICS_WORKSPACE -> "Tactics Workspace";
            case STUDY_DESTINATION -> "Choose a Study";
            case STUDY_WORKSPACE -> "Study Workspace";
            case SETUP -> "Setup";
            case PLAYERS -> "Player Profiles";
            case MY_GAMES -> "My Games";
            case GAME_STATISTICS -> "Game Statistics";
            case KNIGHTSHADE_ARENA -> "Knightshade Arena";
            case MAIN -> "Home";
        };
    }
}
