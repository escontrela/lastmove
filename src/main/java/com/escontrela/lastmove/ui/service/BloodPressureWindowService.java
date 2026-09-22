package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Instant;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import com.escontrela.lastmove.ui.component.statistics.GameStatisticsChartControl;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

/** Owns the optional, non-modal Blood Pressure monitor window. */
@Component
@Lazy
public final class BloodPressureWindowService {

  private static final List<String> METRICS = List.of(
      "depth", "mainNodes", "qNodes", "TT hit / cutoff", "beta cutoff", "PVS re-search",
      "null / LMR", "aspiration retries", "evaluation cache", "workers", "qsearch", "stand-pat",
      "move lists", "quiet checks", "SEE", "search", "NPS", "stopReason", "stop counters");

  private final Stage primaryStage;
  private final ApplicationThemeService themeService;
  private final KnightshadeTelemetryService telemetryService;
  private final FileChooserFactory fileChooserFactory;
  private final BooleanProperty active = new SimpleBooleanProperty(this, "active");
  private final Map<String, javafx.scene.control.Label> liveValues = new HashMap<>();
  private final Map<String, javafx.scene.control.Label> maxValues = new HashMap<>();
  private final Map<String, javafx.scene.control.Label> avgValues = new HashMap<>();
  private final Map<String, String> descriptions = Map.ofEntries(
      Map.entry("depth", "Profundidad máxima completada por la búsqueda."),
      Map.entry("mainNodes", "Nodos principales explorados."), Map.entry("qNodes", "Nodos de búsqueda quiescente."),
      Map.entry("TT hit / cutoff", "Aciertos y cortes de la tabla de transposición."), Map.entry("beta cutoff", "Podas por límite beta."),
      Map.entry("PVS re-search", "Re-búsquedas PVS con ventana completa."), Map.entry("null / LMR", "Intentos null-move y reducciones LMR."),
      Map.entry("aspiration retries", "Reintentos al ampliar la ventana de aspiración."), Map.entry("evaluation cache", "Aciertos y fallos de caché de evaluación."),
      Map.entry("workers", "Trabajadores solicitados y efectivos."), Map.entry("stopReason", "Motivo por el que terminó la búsqueda."),
      Map.entry("qsearch", "Entradas acumuladas en la búsqueda quiescente."),
      Map.entry("stand-pat", "Cortes por evaluación estática y comprobaciones de ahogado."),
      Map.entry("move lists", "Listas de movimientos generadas por quietud."),
      Map.entry("quiet checks", "Jugadas tranquilas examinadas para comprobar jaques."),
      Map.entry("SEE", "Evaluaciones SEE y capturas podadas por SEE."),
      Map.entry("search", "Identidad, posición raíz y tipo de puntuación de esta búsqueda."),
      Map.entry("NPS", "Nodos procesados por segundo: mainNodes y qNodes por tiempo transcurrido."),
      Map.entry("stop counters", "Número acumulado de búsquedas por motivo de parada."));
  private final Map<String, double[]> averages = new HashMap<>();
  private final Map<String, VBox> metricCards = new HashMap<>();
  private final Map<String, Long> maxima = new HashMap<>();
  private final Map<String, BooleanProperty> parameterSelection = new HashMap<>();
  private final AtomicBoolean uiUpdatePending = new AtomicBoolean();
  private volatile SearchTelemetrySnapshot latestSnapshot;
  private long lastRenderNanos;
  private GameStatisticsChartControl mainNodesChart;
  private GameStatisticsChartControl depthChart;
  private GameStatisticsChartControl npsChart;
  private Stage monitorStage;
  private volatile Instant sessionStartedAt;

  public BloodPressureWindowService(Stage primaryStage, ApplicationThemeService themeService,
      KnightshadeTelemetryService telemetryService) {
    this(primaryStage, themeService, telemetryService, new FileChooserFactory());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BloodPressureWindowService(Stage primaryStage, ApplicationThemeService themeService,
      KnightshadeTelemetryService telemetryService, FileChooserFactory fileChooserFactory) {
    this.primaryStage = primaryStage;
    this.themeService = themeService;
    this.telemetryService = telemetryService;
    this.fileChooserFactory = fileChooserFactory;
    active.set(telemetryService.isEnabled());
    active.addListener((ignored, oldValue, newValue) -> telemetryService.setEnabled(newValue));
    telemetryService.subscribe(this::receiveSnapshot);
  }

  public BooleanProperty activeProperty() {
    return active;
  }

  public boolean isActive() {
    return active.get();
  }

  /** Toggles monitoring and opens or hides its floating configuration window. */
  public void toggle() {
    if (isActive()) {
      active.set(false);
      hide();
    } else {
      active.set(true);
      show();
    }
  }

  /** Opens the monitor without changing its enabled state. */
  public void show() {
    if (monitorStage == null) {
      monitorStage = createStage();
    }
    monitorStage.show();
    monitorStage.toFront();
  }

  /** Hides the window; closing it does not disable monitoring. */
  public void hide() {
    if (monitorStage != null) {
      monitorStage.hide();
    }
  }

  private Stage createStage() {
    Stage stage = new Stage();
    stage.setTitle("Blood Pressure");
    stage.initOwner(primaryStage);
    stage.initModality(Modality.NONE);
    stage.setResizable(true);
    stage.setOnCloseRequest(event -> {
      event.consume();
      stage.hide();
    });
    VBox root = content();
    themeService.register(root);
    Scene scene = new Scene(root, 1_220, 900);
    if (latestSnapshot != null) renderSnapshot(latestSnapshot);
    String stylesheet = getClass().getResource("/css/lastmove.css").toExternalForm();
    scene.getStylesheets().add(stylesheet);
    stage.setScene(scene);
    stage.setX(Math.max(20, primaryStage.getX() + primaryStage.getWidth() - 1_240));
    stage.setY(primaryStage.getY() + 80);
    return stage;
  }

  private VBox content() {
    VBox root = new VBox(12);
    root.setPadding(new Insets(20));
    root.setAlignment(Pos.TOP_LEFT);
    root.getStyleClass().addAll("app-shell", "blood-pressure-window");
    Label title = new Label("Blood Pressure");
    title.getStyleClass().add("app-title");
    Label subtitle = new Label("Knightshade search telemetry");
    subtitle.getStyleClass().add("hero-support");
    javafx.scene.control.Button metricsToggle = new javafx.scene.control.Button();
    metricsToggle.getStyleClass().addAll("message-box-button", "message-box-additional-button");
    metricsToggle.getStyleClass().add("blood-pressure-metrics-toggle");
    metricsToggle.setOnAction(event -> active.set(!active.get()));
    Runnable updateToggleLabel = () -> {
      metricsToggle.setText(active.get() ? "Metrics ON" : "Metrics OFF");
      if (active.get()) metricsToggle.getStyleClass().remove("blood-pressure-metrics-off");
      else if (!metricsToggle.getStyleClass().contains("blood-pressure-metrics-off")) metricsToggle.getStyleClass().add("blood-pressure-metrics-off");
    };
    updateToggleLabel.run();
    active.addListener((ignored, oldValue, newValue) -> updateToggleLabel.run());
    GridPane liveMetrics = new GridPane();
    liveMetrics.setHgap(18);
    liveMetrics.setVgap(7);
    for (String metric : METRICS) {
      parameterSelection.put(metric, new SimpleBooleanProperty(telemetryService.visibleMetrics().contains(metric)));
      Label value = new Label("—");
      Label maximum = new Label("—");
      Label average = new Label("—");
      liveValues.put(metric, value);
      maxValues.put(metric, maximum);
      avgValues.put(metric, average);
      Label heading = new Label(metric); heading.getStyleClass().add("blood-pressure-metric-title");
      Label description = new Label(descriptions.get(metric)); description.getStyleClass().add("blood-pressure-metric-description");
      value.getStyleClass().add("blood-pressure-metric-current");
      Label avgCaption = new Label("AVG"); avgCaption.getStyleClass().add("blood-pressure-metric-caption");
      average.getStyleClass().add("blood-pressure-metric-summary-value");
      Label maxCaption = new Label("MAX"); maxCaption.getStyleClass().add("blood-pressure-metric-caption");
      maximum.getStyleClass().add("blood-pressure-metric-summary-value");
      HBox summary = new HBox(6, avgCaption, average, new javafx.scene.layout.Region(), maxCaption, maximum);
      javafx.scene.layout.HBox.setHgrow(summary.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
      VBox card = new VBox(3, heading, description, value, summary);
      javafx.scene.control.Tooltip.install(card, new javafx.scene.control.Tooltip(descriptions.get(metric)));
      card.getStyleClass().add("blood-pressure-metric-card");
      if ("stop counters".equals(metric)) card.getStyleClass().add("blood-pressure-stop-counters-card");
      metricCards.put(metric, card);
      liveMetrics.add(card, liveMetrics.getChildren().size() % 2, liveMetrics.getChildren().size() / 2);
      boolean visible = telemetryService.visibleMetrics().contains(metric);
      card.setVisible(visible); card.setManaged(visible);
    }
    mainNodesChart = new GameStatisticsChartControl();
    configureChartHeight(mainNodesChart);
    mainNodesChart.renderTelemetry(List.of(), "Main nodes");
    depthChart = new GameStatisticsChartControl();
    configureChartHeight(depthChart);
    depthChart.renderTelemetry(List.of(), "Depth");
    npsChart = new GameStatisticsChartControl();
    configureChartHeight(npsChart);
    npsChart.renderTelemetry(List.of(), "NPS");
    Label liveTitle = new Label("Live metrics");
    liveTitle.getStyleClass().add("card-title");
    Label liveHint = new Label("Values appear when Knightshade is thinking.");
    liveHint.getStyleClass().add("hero-support");
    javafx.scene.control.Button export = new javafx.scene.control.Button("Export CSV");
    export.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    export.setOnAction(event -> exportCsv());
    export.disableProperty().bind(active.not());
    HBox actions = new HBox(10, metricsToggle, export);
    actions.setAlignment(Pos.CENTER_LEFT);
    Label chartsTitle = new Label("Search progress");
    chartsTitle.getStyleClass().add("card-title");
    javafx.scene.control.ScrollPane cardsScroll = new javafx.scene.control.ScrollPane(liveMetrics);
    cardsScroll.setFitToWidth(true);
    cardsScroll.setMinHeight(0);
    cardsScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
    cardsScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
    cardsScroll.getStyleClass().add("blood-pressure-cards-scroll");
    VBox.setVgrow(cardsScroll, javafx.scene.layout.Priority.ALWAYS);
    VBox chartsPane = new VBox(10, chartsTitle, mainNodesChart, depthChart, npsChart);
    chartsPane.getStyleClass().add("blood-pressure-charts-pane");
    HBox.setHgrow(chartsPane, javafx.scene.layout.Priority.ALWAYS);
    VBox cardsPane = new VBox(10, liveTitle, liveHint, cardsScroll);
    cardsPane.setPrefWidth(470);
    cardsPane.setMinWidth(460);
    VBox.setVgrow(cardsScroll, javafx.scene.layout.Priority.ALWAYS);
    HBox dashboard = new HBox(20, chartsPane, cardsPane);
    VBox.setVgrow(dashboard, javafx.scene.layout.Priority.ALWAYS);
    root.getChildren().addAll(title, subtitle, actions, new Separator(), dashboard);
    return root;
  }

  private static void configureChartHeight(GameStatisticsChartControl chart) {
    chart.setMinHeight(150);
    chart.setPrefHeight(150);
    chart.setMaxHeight(150);
  }

  private void exportCsv() {
    var samples = telemetryService.samples();
    if (samples.isEmpty() || monitorStage == null) return;
    fileChooserFactory.chooseCsvExportFile(monitorStage, "knightshade-telemetry")
        .ifPresent(file -> {
          StringBuilder csv = new StringBuilder("gameId,searchId,event,searchStartedAt,observedAt,rootFen,sideToMove,fullmoveNumber,limits,engineVersion,engineBuild,positionHistory,depth,score,scoreType,bestMove,elapsedMillis,parameters,mainNodes,qNodes,nps,ttProbes,ttHits,ttCutoffs,betaCutoffs,pvsResearches,nullMoveAttempts,nullMoveCutoffs,lmrApplications,lmrResearches,aspirationRetries,evaluationCacheHits,evaluationCacheMisses,requestedWorkers,activeWorkers,quiescenceEntries,standPatCutoffs,stalemateChecks,moveListsGenerated,quietChecksExamined,seeEvaluations,seePrunes,stopReason\n");
          String parameters = String.join("|", telemetryService.visibleMetrics());
          for (SearchTelemetrySnapshot s : samples) {
            var context = s.context();
            csv.append(csvValue(context.gameId())).append(',').append(csvValue(context.searchId())).append(',').append(s.event()).append(',')
                .append(context.startedAt()).append(',').append(s.observedAt()).append(',').append(csvValue(context.rootFen())).append(',')
                .append(context.sideToMove()).append(',').append(context.fullmoveNumber()).append(',').append(csvValue(context.limits())).append(',')
                .append(csvValue(context.engineVersion())).append(',').append(csvValue(context.engineBuild())).append(',')
                .append(csvValue(String.join("|", context.positionHistory()))).append(',').append(s.depth()).append(',').append(s.score()).append(',')
                .append(s.isMateScore() ? "MATE" : "CENTIPAWNS").append(',').append(csvValue(s.bestMove())).append(',').append(s.elapsedMillis()).append(',').append(csvValue(parameters)).append(',')
                .append(s.mainNodes()).append(',').append(s.qNodes()).append(',').append(nps(s)).append(',').append(s.ttProbes()).append(',').append(s.ttHits()).append(',').append(s.ttCutoffs()).append(',')
                .append(s.betaCutoffs()).append(',').append(s.pvsResearches()).append(',').append(s.nullMoveAttempts()).append(',')
                .append(s.nullMoveCutoffs()).append(',').append(s.lmrApplications()).append(',').append(s.lmrResearches()).append(',')
                .append(s.aspirationRetries()).append(',').append(s.evaluationCacheHits()).append(',').append(s.evaluationCacheMisses()).append(',')
                .append(s.requestedWorkers()).append(',').append(s.activeWorkers()).append(',').append(s.quiescenceEntries()).append(',')
                .append(s.standPatCutoffs()).append(',').append(s.stalemateChecks()).append(',').append(s.moveListsGenerated()).append(',')
                .append(s.quietChecksExamined()).append(',').append(s.seeEvaluations()).append(',').append(s.seePrunes()).append(',')
                .append(s.stopReason()).append('\n');
          }
          try { Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8); }
          catch (IOException ignored) { }
        });
  }

  private static String csvValue(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
  }

  private void receiveSnapshot(SearchTelemetrySnapshot snapshot) {
    if (telemetryService.sessionStartedAt() != null && !telemetryService.sessionStartedAt().equals(sessionStartedAt)) {
      maxima.clear();
      averages.clear();
      sessionStartedAt = telemetryService.sessionStartedAt();
    }
    latestSnapshot = snapshot;
    if (uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void flushSnapshot() {
    if (mainNodesChart == null || depthChart == null || npsChart == null) {
      uiUpdatePending.set(false);
      return;
    }
    long intervalNanos = 1_000_000_000L / Math.max(1, telemetryService.refreshFrequency());
    long elapsed = System.nanoTime() - lastRenderNanos;
    if (lastRenderNanos != 0 && elapsed < intervalNanos) {
      PauseTransition delay = new PauseTransition(
          Duration.millis(Math.max(1.0, (intervalNanos - elapsed) / 1_000_000.0)));
      delay.setOnFinished(event -> flushSnapshot());
      delay.play();
      return;
    }
    SearchTelemetrySnapshot snapshot = latestSnapshot;
    if (snapshot != null) {
      renderSnapshot(snapshot);
      List<SearchTelemetrySnapshot> samples = telemetryService.samples();
      List<SearchTelemetrySnapshot> iterations = samples.stream()
          .filter(s -> s.event() == com.knightshade.engine.api.SearchTelemetryEvent.ITERATION_COMPLETED)
          .toList();
      mainNodesChart.renderTelemetry(iterations.stream().map(SearchTelemetrySnapshot::mainNodes).toList(), "Main nodes");
      depthChart.renderTelemetry(iterations.stream().map(s -> (long) s.depth()).toList(), "Depth");
      npsChart.renderTelemetry(iterations.stream().map(this::nps).toList(), "NPS");
      lastRenderNanos = System.nanoTime();
    }
    uiUpdatePending.set(false);
    if (latestSnapshot != snapshot && uiUpdatePending.compareAndSet(false, true)) {
      Platform.runLater(this::flushSnapshot);
    }
  }

  private void renderSnapshot(SearchTelemetrySnapshot snapshot) {
    for (String metric : METRICS) {
      VBox card = metricCards.get(metric);
      if (card != null) { boolean visible = telemetryService.visibleMetrics().contains(metric); card.setVisible(visible); card.setManaged(visible); }
    }
    update("depth", snapshot.depth()); update("mainNodes", snapshot.mainNodes()); update("qNodes", snapshot.qNodes());
    update("TT hit / cutoff", snapshot.ttHits(), snapshot.ttCutoffs()); update("beta cutoff", snapshot.betaCutoffs());
    update("PVS re-search", snapshot.pvsResearches()); update("null / LMR", snapshot.nullMoveCutoffs(), snapshot.lmrApplications());
    update("aspiration retries", snapshot.aspirationRetries()); update("evaluation cache", snapshot.evaluationCacheHits(), snapshot.evaluationCacheMisses());
    update("workers", snapshot.activeWorkers(), snapshot.requestedWorkers());
    update("qsearch", snapshot.quiescenceEntries()); update("stand-pat", snapshot.standPatCutoffs(), snapshot.stalemateChecks());
    update("move lists", snapshot.moveListsGenerated()); update("quiet checks", snapshot.quietChecksExamined());
    update("SEE", snapshot.seeEvaluations(), snapshot.seePrunes());
    updateText("search", snapshot.context().gameId() + " · " + snapshot.context().searchId().substring(0, 8)
        + " · " + (snapshot.isMateScore() ? "MATE" : "CP"));
    update("NPS", nps(snapshot)); updateText("stopReason", snapshot.event() + " · " + snapshot.stopReason());
    updateText("stop counters", "TIME_LIMIT " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.TIME_LIMIT, 0L)
        + " / DEPTH " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.DEPTH_LIMIT, 0L)
        + " / MATE " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.MATE, 0L)
        + " / CANCELLED " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.CANCELLED, 0L));
  }

  /** Derived on the JavaFX side from completed-depth snapshots; never in the search hot path. */
  private long nps(SearchTelemetrySnapshot snapshot) {
    return snapshot.elapsedMillis() <= 0 ? 0L : (snapshot.mainNodes() + snapshot.qNodes()) * 1_000L / snapshot.elapsedMillis();
  }

  private void setValue(String metric, Object value) {
    javafx.scene.control.Label label = liveValues.get(metric);
    if (label != null) label.setText(String.valueOf(value));
  }

  private void update(String metric, long... values) {
    StringBuilder current = new StringBuilder(); StringBuilder maximum = new StringBuilder();
    double[] sum = averages.computeIfAbsent(metric, k -> new double[values.length + 1]); sum[0]++;
    for (int i = 0; i < values.length; i++) { long value = values[i]; sum[i + 1] += value;
      if (current.length() > 0) { current.append(" / "); maximum.append(" / "); }
      current.append(value); String key = metric + "#" + i; long max = Math.max(maxima.getOrDefault(key, 0L), value);
      maxima.put(key, max); maximum.append(max);
    }
    StringBuilder average = new StringBuilder();
    for (int i = 0; i < values.length; i++) { if (i > 0) average.append(" / "); average.append(Math.round(sum[i + 1] / sum[0])); }
    setValue(metric, current); setAverageValue(metric, average); setMaxValue(metric, maximum);
  }
  private void updateText(String metric, Object value) { setValue(metric, value); setAverageValue(metric, "—"); setMaxValue(metric, "—"); }
  private void setMaxValue(String metric, Object value) { var label = maxValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
  private void setAverageValue(String metric, Object value) { var label = avgValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
}
