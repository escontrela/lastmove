package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.computer.ComputerEngineHealth;
import com.escontrela.lastmove.application.computer.ComputerEngineIds;
import com.escontrela.lastmove.application.service.ComputerEngineHealthService;
import com.escontrela.lastmove.application.service.ComputerEngineSettingsService;
import com.escontrela.lastmove.application.service.KnightshadeArenaSettingsService;
import com.escontrela.lastmove.application.service.PositionAnalysisService;
import com.escontrela.lastmove.application.arena.KnightshadeArenaSettings;
import com.escontrela.lastmove.ui.component.header.ApplicationHeader;
import com.escontrela.lastmove.ui.component.header.HeaderAction;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.service.ApplicationThemeService;
import com.escontrela.lastmove.ui.service.BoardAppearancePreferencesService;
import com.escontrela.lastmove.ui.service.StartupPreferencesService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.util.StringConverter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Controller for the application setup screen. */
@Component
public class SetupScreenController implements UiScreenController {

    private final UiFlowManager uiFlowManager;
    private final ApplicationThemeService themeService;
    private final StartupPreferencesService startupPreferencesService;
    private final BoardAppearancePreferencesService boardAppearancePreferencesService;
    private final ComputerEngineSettingsService computerEngineSettingsService;
    private final ComputerEngineHealthService computerEngineHealthService;
    private final PositionAnalysisService positionAnalysisService;
    private final KnightshadeArenaSettingsService knightshadeArenaSettingsService;

    @FXML
    private BorderPane root;
    @FXML
    private CheckBox nightModeCheckBox;
    @FXML
    private CheckBox showSplashCheckBox;
    @FXML
    private CheckBox boardVisualEffectsCheckBox;
    @FXML
    private ApplicationHeader applicationHeader;
    @FXML
    private TextField sunfishExecutablePathField;
    @FXML
    private Button testSunfishButton;
    @FXML
    private Label sunfishValidationLabel;
    @FXML
    private TextField maiaExecutablePathField;
    @FXML
    private TextField maiaWeightsPathField;
    @FXML
    private Button testMaiaButton;
    @FXML
    private Label maiaValidationLabel;
    @FXML
    private ComboBox<Duration> knightshadeThinkingTimeCombo;
    @FXML
    private ComboBox<ComputerEngineDescriptor> analysisEngineCombo;
    @FXML
    private CheckBox analysisEngineDefaultCheckBox;
    @FXML
    private PasswordField lichessBotTokenField;
    @FXML
    private Spinner<Integer> arenaMaximumGamesSpinner;
    @FXML
    private CheckBox arenaAutomaticAcceptanceCheckBox;
    @FXML
    private Button validateLichessAccountButton;
    @FXML
    private Label lichessValidationLabel;

    private static final List<Duration> THINKING_TIME_PRESETS = List.of(
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30));

    private static final StringConverter<Duration> THINKING_TIME_CONVERTER =
            new StringConverter<>() {
                @Override
                public String toString(Duration duration) {
                    if (duration == null) {
                        return "";
                    }
                    long millis = duration.toMillis();
                    if (millis < 1000) {
                        return millis + " ms";
                    }
                    if (millis % 1000 == 0) {
                        return (millis / 1000) + " s";
                    }
                    return String.format("%.1f s", millis / 1000.0);
                }

                @Override
                public Duration fromString(String value) {
                    throw new UnsupportedOperationException("The thinking time selector is not editable");
                }
            };

    private static final StringConverter<ComputerEngineDescriptor> ENGINE_CONVERTER =
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
            };

    private boolean savedNightMode;
    private boolean savedSplashScreen;
    private boolean savedBoardVisualEffects;
    private String savedSunfishExecutablePath;
    private String savedMaiaExecutablePath;
    private String savedMaiaWeightsPath;
    private Duration savedKnightshadeThinkingTime;
    private String savedAnalysisEngineDefaultId;
    private KnightshadeArenaSettings savedArenaSettings;
    private boolean arenaTokenChanged;
    private boolean loadingArenaToken;

    public SetupScreenController(
            @Lazy UiFlowManager uiFlowManager,
            ApplicationThemeService themeService,
            StartupPreferencesService startupPreferencesService,
            BoardAppearancePreferencesService boardAppearancePreferencesService,
            ComputerEngineSettingsService computerEngineSettingsService,
            ComputerEngineHealthService computerEngineHealthService,
            PositionAnalysisService positionAnalysisService,
            KnightshadeArenaSettingsService knightshadeArenaSettingsService) {
        this.uiFlowManager = uiFlowManager;
        this.themeService = themeService;
        this.startupPreferencesService = startupPreferencesService;
        this.boardAppearancePreferencesService = boardAppearancePreferencesService;
        this.computerEngineSettingsService = computerEngineSettingsService;
        this.computerEngineHealthService = computerEngineHealthService;
        this.positionAnalysisService = positionAnalysisService;
        this.knightshadeArenaSettingsService = knightshadeArenaSettingsService;
    }

    @FXML
    public void initialize() {
        root.getProperties().put("controller", this);
        nightModeCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        showSplashCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        boardVisualEffectsCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        sunfishExecutablePathField.textProperty().addListener((ignored, oldValue, newValue) -> {
            clearSunfishValidation();
            updateApplyButtonVisibility();
        });
        maiaExecutablePathField.textProperty().addListener((ignored, oldValue, newValue) -> {
            clearMaiaValidation();
            updateApplyButtonVisibility();
        });
        maiaWeightsPathField.textProperty().addListener((ignored, oldValue, newValue) -> {
            clearMaiaValidation();
            updateApplyButtonVisibility();
        });
        knightshadeThinkingTimeCombo.setItems(FXCollections.observableArrayList(THINKING_TIME_PRESETS));
        knightshadeThinkingTimeCombo.setConverter(THINKING_TIME_CONVERTER);
        knightshadeThinkingTimeCombo.valueProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        analysisEngineCombo.setConverter(ENGINE_CONVERTER);
        analysisEngineCombo.valueProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        analysisEngineDefaultCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        arenaMaximumGamesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, KnightshadeArenaSettings.MAXIMUM_CONCURRENT_GAMES_LIMIT,
                KnightshadeArenaSettings.DEFAULT_MAXIMUM_CONCURRENT_GAMES));
        arenaMaximumGamesSpinner.valueProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        arenaAutomaticAcceptanceCheckBox.selectedProperty().addListener((ignored, oldValue, newValue) ->
                updateApplyButtonVisibility());
        lichessBotTokenField.textProperty().addListener((ignored, oldValue, newValue) -> {
            if (!loadingArenaToken) {
                arenaTokenChanged = true;
                clearLichessValidation();
                updateApplyButtonVisibility();
            }
        });
    }

    @Override
    public void onShow() {
        savedNightMode = themeService.currentThemeMode().isNightMode();
        savedSplashScreen = startupPreferencesService.isSplashScreenEnabled();
        savedBoardVisualEffects = boardAppearancePreferencesService.isBoardVisualEffectsEnabled();
        savedSunfishExecutablePath = computerEngineSettingsService
                .sunfishSettings()
                .executablePath()
                .toString();
        savedMaiaExecutablePath = computerEngineSettingsService
                .maiaExecutable()
                .map(Object::toString)
                .orElse("");
        savedMaiaWeightsPath = computerEngineSettingsService
                .maiaWeightsLocation()
                .toString();
        savedKnightshadeThinkingTime = computerEngineSettingsService
                .thinkingTime(ComputerEngineIds.KNIGHTSHADE);
        nightModeCheckBox.setSelected(savedNightMode);
        showSplashCheckBox.setSelected(savedSplashScreen);
        boardVisualEffectsCheckBox.setSelected(savedBoardVisualEffects);
        sunfishExecutablePathField.setText(savedSunfishExecutablePath);
        maiaExecutablePathField.setText(savedMaiaExecutablePath);
        maiaWeightsPathField.setText(savedMaiaWeightsPath);
        if (!knightshadeThinkingTimeCombo.getItems().contains(savedKnightshadeThinkingTime)) {
            knightshadeThinkingTimeCombo.getItems().add(savedKnightshadeThinkingTime);
        }
        knightshadeThinkingTimeCombo.getSelectionModel().select(savedKnightshadeThinkingTime);
        analysisEngineCombo.setItems(FXCollections.observableArrayList(positionAnalysisService.availableEngines()));
        savedAnalysisEngineDefaultId =
                computerEngineSettingsService.defaultAnalysisEngineId().orElse(null);
        analysisEngineDefaultCheckBox.setSelected(savedAnalysisEngineDefaultId != null);
        selectAnalysisEngine(positionAnalysisService.defaultEngineId());
        savedArenaSettings = knightshadeArenaSettingsService.settings();
        arenaMaximumGamesSpinner.getValueFactory().setValue(savedArenaSettings.maximumConcurrentGames());
        arenaAutomaticAcceptanceCheckBox.setSelected(savedArenaSettings.automaticChallengeAcceptance());
        clearTokenField();
        arenaTokenChanged = false;
        clearSunfishValidation();
        clearMaiaValidation();
        clearLichessValidation();
        updateApplyButtonVisibility();
    }

    @FXML
    public void applySettings() {
        try {
            savedSunfishExecutablePath = computerEngineSettingsService
                    .updateSunfishExecutable(sunfishExecutablePathField.getText())
                    .executablePath()
                    .toString();
        } catch (IllegalArgumentException exception) {
            showSunfishValidation(exception.getMessage(), false);
            return;
        }
        try {
            applyMaiaSettings();
        } catch (IllegalArgumentException exception) {
            showMaiaValidation(exception.getMessage(), false);
            return;
        }
        savedKnightshadeThinkingTime = computerEngineSettingsService.updateThinkingTime(
                ComputerEngineIds.KNIGHTSHADE, knightshadeThinkingTimeCombo.getValue());
        savedAnalysisEngineDefaultId = effectiveAnalysisEngineDefaultId();
        computerEngineSettingsService.updateDefaultAnalysisEngineId(
                Optional.ofNullable(savedAnalysisEngineDefaultId));
        applyArenaSettings();
        themeService.setNightMode(nightModeCheckBox.isSelected());
        startupPreferencesService.setSplashScreenEnabled(showSplashCheckBox.isSelected());
        boardAppearancePreferencesService.setBoardVisualEffectsEnabled(boardVisualEffectsCheckBox.isSelected());
        savedNightMode = nightModeCheckBox.isSelected();
        savedSplashScreen = showSplashCheckBox.isSelected();
        savedBoardVisualEffects = boardVisualEffectsCheckBox.isSelected();
        updateApplyButtonVisibility();
    }

    private void applyMaiaSettings() {
        String executable = trimmed(maiaExecutablePathField.getText());
        if (executable.isEmpty()) {
            computerEngineSettingsService.clearMaiaExecutable();
            savedMaiaExecutablePath = "";
        } else {
            savedMaiaExecutablePath = computerEngineSettingsService
                    .updateMaiaExecutable(executable)
                    .executablePath()
                    .toString();
        }
        savedMaiaWeightsPath = computerEngineSettingsService
                .updateMaiaWeightsLocation(maiaWeightsPathField.getText())
                .executablePath()
                .toString();
    }

    private void applyArenaSettings() {
        if (arenaTokenChanged) {
            String token = trimmed(lichessBotTokenField.getText());
            if (token.isEmpty()) {
                knightshadeArenaSettingsService.clearBotToken();
            } else {
                knightshadeArenaSettingsService.updateBotToken(token);
            }
            clearTokenField();
            arenaTokenChanged = false;
        }
        savedArenaSettings = new KnightshadeArenaSettings(
                arenaMaximumGamesSpinner.getValue(), arenaAutomaticAcceptanceCheckBox.isSelected());
        knightshadeArenaSettingsService.updateSettings(savedArenaSettings);
        clearLichessValidation();
    }

    /** Validates only a token already saved in user preferences, never a token in an unsaved field. */
    @FXML
    public void validateLichessAccount() {
        validateLichessAccountButton.setDisable(true);
        showLichessValidation("Validating the configured Lichess bot account…", null);
        CompletableFuture.supplyAsync(knightshadeArenaSettingsService::validateConfiguredBotAccount)
                .whenComplete((account, failure) -> Platform.runLater(() -> finishLichessValidation(account, failure)));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private void selectAnalysisEngine(String engineId) {
        for (ComputerEngineDescriptor engine : analysisEngineCombo.getItems()) {
            if (engine.id().equals(engineId)) {
                analysisEngineCombo.getSelectionModel().select(engine);
                return;
            }
        }
        analysisEngineCombo.getSelectionModel().selectFirst();
    }

    private String effectiveAnalysisEngineDefaultId() {
        if (!analysisEngineDefaultCheckBox.isSelected()) {
            return null;
        }
        ComputerEngineDescriptor selected = analysisEngineCombo.getValue();
        return selected == null ? null : selected.id();
    }

    /** Runs a non-blocking end-to-end UCI probe against the saved Sunfish executable. */
    @FXML
    public void testSunfishConnection() {
        testSunfishButton.setDisable(true);
        showSunfishValidation("Checking wrapper, Python runtime and UCI response…", null);
        computerEngineHealthService.checkSunfish().whenComplete((health, failure) ->
                Platform.runLater(() -> finishSunfishCheck(health, failure)));
    }

    /** Runs a non-blocking end-to-end UCI probe against the configured Maia (lc0 + weights). */
    @FXML
    public void testMaiaConnection() {
        testMaiaButton.setDisable(true);
        showMaiaValidation("Checking lc0 and the Maia weights file…", null);
        computerEngineHealthService.checkMaia().whenComplete((health, failure) ->
                Platform.runLater(() -> finishMaiaCheck(health, failure)));
    }

    @FXML
    public void backToMain() {
        uiFlowManager.show(UiScreenId.MAIN);
    }

    private void updateApplyButtonVisibility() {
        boolean hasUnsavedChanges = hasUnsavedChanges();
        applicationHeader.setContextActions(List.of(new HeaderAction(
                "Apply setup changes",
                "Apply settings",
                "/images/save_35dp_000000.png",
                "/images/save_35dp_FFFFFF.png",
                event -> applySettings(),
                !hasUnsavedChanges)));
        testSunfishButton.setDisable(hasUnsavedChanges || savedSunfishExecutablePath == null);
        testMaiaButton.setDisable(hasUnsavedChanges);
        validateLichessAccountButton.setDisable(hasUnsavedChanges || !knightshadeArenaSettingsService.hasBotToken());
        if (hasUnsavedChanges
                && !sunfishExecutablePathField.getText().trim().equals(savedSunfishExecutablePath)) {
            showSunfishValidation("Apply the executable path before testing the connection.", null);
        }
        if (hasUnsavedChanges
                && (!trimmed(maiaExecutablePathField.getText()).equals(savedMaiaExecutablePath)
                        || !trimmed(maiaWeightsPathField.getText()).equals(savedMaiaWeightsPath))) {
            showMaiaValidation("Apply the Maia settings before testing the connection.", null);
        }
    }

    private boolean hasUnsavedChanges() {
        return nightModeCheckBox.isSelected() != savedNightMode
                || showSplashCheckBox.isSelected() != savedSplashScreen
                || boardVisualEffectsCheckBox.isSelected() != savedBoardVisualEffects
                || !sunfishExecutablePathField.getText().trim().equals(savedSunfishExecutablePath)
                || !trimmed(maiaExecutablePathField.getText()).equals(savedMaiaExecutablePath)
                || !trimmed(maiaWeightsPathField.getText()).equals(savedMaiaWeightsPath)
                || !Objects.equals(
                        knightshadeThinkingTimeCombo.getValue(), savedKnightshadeThinkingTime)
                || !Objects.equals(
                        effectiveAnalysisEngineDefaultId(), savedAnalysisEngineDefaultId)
                || arenaTokenChanged
                || !Objects.equals(new KnightshadeArenaSettings(
                        arenaMaximumGamesSpinner.getValue(), arenaAutomaticAcceptanceCheckBox.isSelected()),
                        savedArenaSettings);
    }

    private void finishSunfishCheck(ComputerEngineHealth health, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            showSunfishValidation("Sunfish check failed: " + cause.getMessage(), false);
        } else {
            showSunfishValidation(health.message(), health.available());
        }
        updateApplyButtonVisibility();
    }

    private void clearSunfishValidation() {
        sunfishValidationLabel.setText("");
        sunfishValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
    }

    private void showSunfishValidation(String message, Boolean successful) {
        sunfishValidationLabel.setText(message == null ? "" : message);
        sunfishValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
        if (successful != null) {
            sunfishValidationLabel.getStyleClass().add(
                    successful ? "settings-validation-success" : "settings-validation-error");
        }
    }

    private void finishMaiaCheck(ComputerEngineHealth health, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            showMaiaValidation("Maia check failed: " + cause.getMessage(), false);
        } else {
            showMaiaValidation(health.message(), health.available());
        }
        updateApplyButtonVisibility();
    }

    private void clearMaiaValidation() {
        maiaValidationLabel.setText("");
        maiaValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
    }

    private void showMaiaValidation(String message, Boolean successful) {
        maiaValidationLabel.setText(message == null ? "" : message);
        maiaValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
        if (successful != null) {
            maiaValidationLabel.getStyleClass().add(
                    successful ? "settings-validation-success" : "settings-validation-error");
        }
    }

    private void finishLichessValidation(
            com.escontrela.lastmove.application.arena.LichessBotAccount account, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            showLichessValidation(cause.getMessage() == null ? "Could not validate the Lichess bot account." : cause.getMessage(), false);
        } else {
            showLichessValidation("Connected to Lichess bot account " + account.username() + ".", true);
        }
        updateApplyButtonVisibility();
    }

    private void clearTokenField() {
        loadingArenaToken = true;
        lichessBotTokenField.clear();
        loadingArenaToken = false;
    }

    private void clearLichessValidation() {
        lichessValidationLabel.setText("");
        lichessValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
    }

    private void showLichessValidation(String message, Boolean successful) {
        lichessValidationLabel.setText(message == null ? "" : message);
        lichessValidationLabel.getStyleClass().removeAll(
                "settings-validation-success", "settings-validation-error");
        if (successful != null) {
            lichessValidationLabel.getStyleClass().add(
                    successful ? "settings-validation-success" : "settings-validation-error");
        }
    }
}
