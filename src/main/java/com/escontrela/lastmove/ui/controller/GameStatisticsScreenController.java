package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.service.ComputerGameService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.GameStatisticsService;
import com.escontrela.lastmove.application.service.KnightshadeArenaSettingsService;
import com.escontrela.lastmove.application.service.PlayerService;
import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.statistics.GameResultCounts;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import com.escontrela.lastmove.ui.component.evaluation.EngineSelectorModal;
import com.escontrela.lastmove.ui.component.player.PlayerSelectorModal;
import com.escontrela.lastmove.ui.component.statistics.GameStatisticsChartControl;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.springframework.stereotype.Component;

/** Thin JavaFX controller for the selected player's derived game statistics. */
@Component
public final class GameStatisticsScreenController implements UiScreenController {
  private static final String ALL_ENGINES = "all-engines";
  private final GameStatisticsService statistics; private final CurrentUserService currentUser; private final ComputerGameService games; private final PlayerService players; private final KnightshadeArenaSettingsService arenaSettings;
  private final AtomicLong refreshVersion = new AtomicLong();
  @FXML private StackPane root; @FXML private DatePicker fromDate; @FXML private DatePicker toDate; @FXML private ComboBox<StatisticsGranularity> granularity;
  @FXML private Button playerLabel; @FXML private Button engineLabel; @FXML private Label totalLabel; @FXML private Label wonLabel; @FXML private Label lostLabel; @FXML private Label drawnLabel; @FXML private Label statusLabel;
  @FXML private Button chartModeButton;
  @FXML private GameStatisticsChartControl chart; @FXML private EngineSelectorModal engineSelector; @FXML private PlayerSelectorModal playerSelector;
  private Optional<String> selectedEngine = Optional.empty();
  private Optional<PlayerSummary> selectedPlayer = Optional.empty();
  private GameStatisticsChartControl.DisplayMode chartMode = GameStatisticsChartControl.DisplayMode.OUTCOME_BARS;
  public GameStatisticsScreenController(GameStatisticsService statistics, CurrentUserService currentUser, ComputerGameService games, PlayerService players, KnightshadeArenaSettingsService arenaSettings) { this.statistics = statistics; this.currentUser = currentUser; this.games = games; this.players = players; this.arenaSettings = arenaSettings; }
  @FXML public void initialize() {
    root.getProperties().put("controller", this); granularity.setItems(FXCollections.observableArrayList(StatisticsGranularity.values()));
    granularity.setConverter(new javafx.util.StringConverter<>() { public String toString(StatisticsGranularity value) { return value == null ? "" : value.name().substring(0, 1) + value.name().substring(1).toLowerCase(); } public StatisticsGranularity fromString(String value) { return StatisticsGranularity.valueOf(value.toUpperCase()); } });
    fromDate.setValue(LocalDate.now().minusDays(6)); toDate.setValue(LocalDate.now()); granularity.setValue(StatisticsGranularity.DAY); chart.setDisplayMode(chartMode);
    engineSelector.setOnEngineSelected(event -> { selectedEngine = ALL_ENGINES.equals(event.engineId()) ? Optional.empty() : Optional.of(event.engineId()); engineLabel.setText(engineName()); refresh(); });
    engineSelector.setOnCancel(event -> statusLabel.setText("Statistics unchanged"));
    playerSelector.setOnPlayerSelected(event -> { selectedPlayer = Optional.of(event.player()); playerLabel.setText(event.player().fullName()); refresh(); });
    playerSelector.setOnCancel(event -> statusLabel.setText("Statistics unchanged"));
  }
  @Override public void onShow() { synchronizeConfiguredBot(); if (selectedPlayer.isEmpty()) selectedPlayer = currentUser.selectedPlayerId().flatMap(players::playerSummary); playerLabel.setText(selectedPlayer.map(PlayerSummary::fullName).orElse("Choose player")); refresh(); }
  @FXML public void onFiltersChanged() { refresh(); }
  @FXML public void toggleChartMode() { chartMode = chartMode == GameStatisticsChartControl.DisplayMode.GAME_TREND ? GameStatisticsChartControl.DisplayMode.OUTCOME_BARS : GameStatisticsChartControl.DisplayMode.GAME_TREND; chart.setDisplayMode(chartMode); chartModeButton.setText(chartMode == GameStatisticsChartControl.DisplayMode.GAME_TREND ? "Show outcome bars" : "Show game trend"); }
  @FXML public void chooseEngine() { List<ComputerEngineDescriptor> options = new java.util.ArrayList<>(); options.add(new ComputerEngineDescriptor(ALL_ENGINES, "All engines", "Entire history")); options.addAll(games.availableEngines()); engineSelector.show(options, selectedEngine.orElse(ALL_ENGINES), "Choose engine", "Filter the selected player's completed-game history."); }
  @FXML public void choosePlayer() {
    if (!players.isPersistenceAvailable()) { statusLabel.setText("Player persistence is unavailable."); return; }
    if (arenaSettings.configuredBotAccount().isEmpty() && arenaSettings.hasBotToken()) {
      statusLabel.setText("Validating the configured Knightshade Lichess account…");
      CompletableFuture.supplyAsync(arenaSettings::validateConfiguredBotAccount).whenComplete((account, failure) -> Platform.runLater(() -> {
        if (failure != null) {
          statusLabel.setText("Could not validate Knightshade. Open Settings to check the Lichess token.");
          return;
        }
        players.synchronizeLichessBot(account);
        showPlayerSelector();
      }));
      return;
    }
    showPlayerSelector();
  }

  private void showPlayerSelector() {
    List<PlayerSummary> options = players.listPlayers();
    if (options.isEmpty()) { statusLabel.setText("Create or validate a player profile before viewing statistics."); return; }
    playerSelector.show(options, selectedPlayer.map(summary -> summary.id().value()).orElse(null), "Choose player", "View statistics for an app player or Knightshade. Uncheck to include Lichess participants.", arenaSettings.configuredBotAccount().map(com.escontrela.lastmove.application.arena.LichessBotAccount::id));
  }
  private void refresh() {
    LocalDate from = fromDate.getValue(), to = toDate.getValue(); StatisticsGranularity grouping = granularity.getValue();
    if (from == null || to == null || grouping == null) { statusLabel.setText("Choose a complete date range and grouping."); return; }
    if (from.isAfter(to)) { statusLabel.setText("From date must be on or before To date."); return; }
    long version = refreshVersion.incrementAndGet(); chart.setLoading(true); statusLabel.setText("Loading statistics…");
    selectedPlayer.map(PlayerSummary::id).ifPresentOrElse(player -> CompletableFuture.supplyAsync(() -> statistics.get(new GameStatisticsQuery(player, from, to, grouping, selectedEngine)))
      .whenComplete((result, failure) -> Platform.runLater(() -> { if (version != refreshVersion.get()) return; if (failure != null) { chart.setLoading(false); statusLabel.setText("Could not load statistics."); return; } render(result, grouping); })),
      () -> { chart.render(List.of(), formatFor(grouping)); setCounts(new GameResultCounts(0, 0, 0)); statusLabel.setText("Choose a player profile to see statistics."); });
  }
  private void synchronizeConfiguredBot() { if (!players.isPersistenceAvailable()) return; arenaSettings.configuredBotAccount().ifPresent(players::synchronizeLichessBot); }
  private void render(GameStatistics value, StatisticsGranularity grouping) { chart.setDisplayMode(chartMode); chart.render(value.buckets(), formatFor(grouping)); setCounts(value.results()); statusLabel.setText(value.results().total() == 0 ? "No completed games in this period." : "Statistics ready for " + engineName() + "."); }
  private void setCounts(GameResultCounts counts) { totalLabel.setText(counts.total() + " games"); wonLabel.setText(Long.toString(counts.won())); lostLabel.setText(Long.toString(counts.lost())); drawnLabel.setText(Long.toString(counts.drawn())); }
  private DateTimeFormatter formatFor(StatisticsGranularity grouping) { return switch (grouping) { case DAY -> DateTimeFormatter.ofPattern("d MMM"); case MONTH -> DateTimeFormatter.ofPattern("MMM uuuu"); case YEAR -> DateTimeFormatter.ofPattern("uuuu"); }; }
  private String engineName() { return selectedEngine.flatMap(id -> games.availableEngines().stream().filter(engine -> engine.id().equals(id)).map(ComputerEngineDescriptor::displayName).findFirst()).orElse("All engines"); }
}
