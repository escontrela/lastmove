package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.computer.ComputerEngineDescriptor;
import com.escontrela.lastmove.application.service.ComputerGameService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.GameStatisticsService;
import com.escontrela.lastmove.application.statistics.GameStatisticsQuery;
import com.escontrela.lastmove.domain.statistics.GameResultCounts;
import com.escontrela.lastmove.domain.statistics.GameStatistics;
import com.escontrela.lastmove.domain.statistics.StatisticsGranularity;
import com.escontrela.lastmove.ui.component.evaluation.EngineSelectorModal;
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
  private final GameStatisticsService statistics; private final CurrentUserService currentUser; private final ComputerGameService games;
  private final AtomicLong refreshVersion = new AtomicLong();
  @FXML private StackPane root; @FXML private DatePicker fromDate; @FXML private DatePicker toDate; @FXML private ComboBox<StatisticsGranularity> granularity;
  @FXML private Button engineLabel; @FXML private Label totalLabel; @FXML private Label wonLabel; @FXML private Label lostLabel; @FXML private Label drawnLabel; @FXML private Label statusLabel;
  @FXML private Button chartModeButton;
  @FXML private GameStatisticsChartControl chart; @FXML private EngineSelectorModal engineSelector;
  private Optional<String> selectedEngine = Optional.empty();
  private GameStatisticsChartControl.DisplayMode chartMode = GameStatisticsChartControl.DisplayMode.OUTCOME_BARS;
  public GameStatisticsScreenController(GameStatisticsService statistics, CurrentUserService currentUser, ComputerGameService games) { this.statistics = statistics; this.currentUser = currentUser; this.games = games; }
  @FXML public void initialize() {
    root.getProperties().put("controller", this); granularity.setItems(FXCollections.observableArrayList(StatisticsGranularity.values()));
    granularity.setConverter(new javafx.util.StringConverter<>() { public String toString(StatisticsGranularity value) { return value == null ? "" : value.name().substring(0, 1) + value.name().substring(1).toLowerCase(); } public StatisticsGranularity fromString(String value) { return StatisticsGranularity.valueOf(value.toUpperCase()); } });
    fromDate.setValue(LocalDate.now().minusDays(6)); toDate.setValue(LocalDate.now()); granularity.setValue(StatisticsGranularity.DAY); chart.setDisplayMode(chartMode);
    engineSelector.setOnEngineSelected(event -> { selectedEngine = ALL_ENGINES.equals(event.engineId()) ? Optional.empty() : Optional.of(event.engineId()); engineLabel.setText(engineName()); refresh(); });
    engineSelector.setOnCancel(event -> statusLabel.setText("Statistics unchanged"));
  }
  @Override public void onShow() { refresh(); }
  @FXML public void onFiltersChanged() { refresh(); }
  @FXML public void toggleChartMode() { chartMode = chartMode == GameStatisticsChartControl.DisplayMode.GAME_TREND ? GameStatisticsChartControl.DisplayMode.OUTCOME_BARS : GameStatisticsChartControl.DisplayMode.GAME_TREND; chart.setDisplayMode(chartMode); chartModeButton.setText(chartMode == GameStatisticsChartControl.DisplayMode.GAME_TREND ? "Show outcome bars" : "Show game trend"); }
  @FXML public void chooseEngine() { List<ComputerEngineDescriptor> options = new java.util.ArrayList<>(); options.add(new ComputerEngineDescriptor(ALL_ENGINES, "All engines", "Entire history")); options.addAll(games.availableEngines()); engineSelector.show(options, selectedEngine.orElse(ALL_ENGINES), "Choose engine", "Filter the selected player's completed-game history."); }
  private void refresh() {
    LocalDate from = fromDate.getValue(), to = toDate.getValue(); StatisticsGranularity grouping = granularity.getValue();
    if (from == null || to == null || grouping == null) { statusLabel.setText("Choose a complete date range and grouping."); return; }
    if (from.isAfter(to)) { statusLabel.setText("From date must be on or before To date."); return; }
    long version = refreshVersion.incrementAndGet(); chart.setLoading(true); statusLabel.setText("Loading statistics…");
    currentUser.selectedPlayerId().ifPresentOrElse(player -> CompletableFuture.supplyAsync(() -> statistics.get(new GameStatisticsQuery(player, from, to, grouping, selectedEngine)))
      .whenComplete((result, failure) -> Platform.runLater(() -> { if (version != refreshVersion.get()) return; if (failure != null) { chart.setLoading(false); statusLabel.setText("Could not load statistics."); return; } render(result, grouping); })),
      () -> { chart.render(List.of(), formatFor(grouping)); setCounts(new GameResultCounts(0, 0, 0)); statusLabel.setText("Select a player profile to see statistics."); });
  }
  private void render(GameStatistics value, StatisticsGranularity grouping) { chart.setDisplayMode(chartMode); chart.render(value.buckets(), formatFor(grouping)); setCounts(value.results()); statusLabel.setText(value.results().total() == 0 ? "No completed games in this period." : "Statistics ready for " + engineName() + "."); }
  private void setCounts(GameResultCounts counts) { totalLabel.setText(counts.total() + " games"); wonLabel.setText(Long.toString(counts.won())); lostLabel.setText(Long.toString(counts.lost())); drawnLabel.setText(Long.toString(counts.drawn())); }
  private DateTimeFormatter formatFor(StatisticsGranularity grouping) { return switch (grouping) { case DAY -> DateTimeFormatter.ofPattern("d MMM"); case MONTH -> DateTimeFormatter.ofPattern("MMM uuuu"); case YEAR -> DateTimeFormatter.ofPattern("uuuu"); }; }
  private String engineName() { return selectedEngine.flatMap(id -> games.availableEngines().stream().filter(engine -> engine.id().equals(id)).map(ComputerEngineDescriptor::displayName).findFirst()).orElse("All engines"); }
}
