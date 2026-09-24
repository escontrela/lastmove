package com.escontrela.lastmove.ui.service;

import com.escontrela.lastmove.application.service.KnightshadeTelemetryService;
import com.knightshade.engine.api.SearchTelemetrySnapshot;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
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
      "depth", "time to depth (ms)", "post-depth time (ms)", "post-depth nodes",
      "mainNodes", "qNodes", "TT hit / cutoff", "beta cutoff", "PVS re-search",
      "null / LMR", "aspiration retries", "mate confirmations", "evaluation cache", "workers", "qsearch", "stand-pat",
      "move lists", "quiet checks", "SEE", "search", "NPS", "stopReason", "stop counters",
      "ponder starts", "ponder hit rate", "ponder reused depth");

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
      Map.entry("time to depth (ms)", "Milisegundos desde el inicio hasta completar la última iteración publicada."),
      Map.entry("post-depth time (ms)", "Milisegundos transcurridos después de completar la última iteración."),
      Map.entry("post-depth nodes", "Nodos explorados después de completar la última iteración."),
      Map.entry("mainNodes", "Nodos principales explorados."), Map.entry("qNodes", "Nodos de búsqueda quiescente."),
      Map.entry("TT hit / cutoff", "Aciertos y cortes de la tabla de transposición."), Map.entry("beta cutoff", "Podas por límite beta."),
      Map.entry("PVS re-search", "Re-búsquedas PVS con ventana completa."), Map.entry("null / LMR", "Intentos null-move y reducciones LMR."),
      Map.entry("aspiration retries", "Reintentos al ampliar la ventana para scores en centipeones."),
      Map.entry("mate confirmations", "Confirmaciones con ventana completa tras detectar un score de mate fuera de ventana."),
      Map.entry("evaluation cache", "Aciertos y fallos de caché de evaluación."),
      Map.entry("workers", "Trabajadores solicitados y efectivos."), Map.entry("stopReason", "Motivo por el que terminó la búsqueda."),
      Map.entry("qsearch", "Entradas acumuladas en la búsqueda quiescente."),
      Map.entry("stand-pat", "Cortes por evaluación estática y comprobaciones de ahogado."),
      Map.entry("move lists", "Listas de movimientos generadas por quietud."),
      Map.entry("quiet checks", "Jugadas tranquilas examinadas para comprobar jaques."),
      Map.entry("SEE", "Evaluaciones SEE y capturas podadas por SEE."),
      Map.entry("search", "Identidad, posición raíz y tipo de puntuación de esta búsqueda."),
      Map.entry("NPS", "Nodos procesados por segundo: mainNodes y qNodes por tiempo transcurrido."),
      Map.entry("stop counters", "Número acumulado de búsquedas por motivo de parada."),
      Map.entry("ponder starts", "Veces que arrancó realmente la predicción mientras esperaba al rival. Cuenta una vez por tarea; requiere Ponder activo en Settings y capacidad libre."),
      Map.entry("ponder hit rate", "Aciertos / (aciertos + fallos). Solo cuenta predicciones finalizadas comparadas con la respuesta real; cancelaciones y omisiones no cuentan."),
      Map.entry("ponder reused depth", "Profundidad completa de continuación adoptada al llegar el turno. AVG y MAX cuentan una vez cada decisión de reutilización."));
  private final Map<String, double[]> averages = new HashMap<>();
  private final Map<String, VBox> metricCards = new HashMap<>();
  private final Map<String, Long> maxima = new HashMap<>();
  private final java.util.Set<SnapshotKey> aggregatedSnapshots = new java.util.HashSet<>();
  private final Map<String, BooleanProperty> parameterSelection = new HashMap<>();
  private final LatestValueRefreshCoalescer<SearchTelemetrySnapshot> refreshCoalescer =
      new LatestValueRefreshCoalescer<>();
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
    if (latestSnapshot != null) renderSnapshot(latestSnapshot, telemetryService.samples());
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
    TilePane liveMetrics = new TilePane();
    liveMetrics.setHgap(6);
    liveMetrics.setVgap(4);
    liveMetrics.setPrefTileWidth(190);
    liveMetrics.setPrefTileHeight(68);
    liveMetrics.setTileAlignment(Pos.CENTER_LEFT);
    liveMetrics.setPrefColumns(5);
    for (String metric : METRICS) {
      parameterSelection.put(metric, new SimpleBooleanProperty(telemetryService.visibleMetrics().contains(metric)));
      Label value = new Label("—");
      Label maximum = new Label("—");
      Label average = new Label("—");
      liveValues.put(metric, value);
      maxValues.put(metric, maximum);
      avgValues.put(metric, average);
      Label heading = new Label(metric); heading.getStyleClass().add("blood-pressure-metric-title");
      value.getStyleClass().add("blood-pressure-metric-current");
      Label avgCaption = new Label("AVG"); avgCaption.getStyleClass().add("blood-pressure-metric-caption");
      average.getStyleClass().add("blood-pressure-metric-summary-value");
      Label maxCaption = new Label("MAX"); maxCaption.getStyleClass().add("blood-pressure-metric-caption");
      maximum.getStyleClass().add("blood-pressure-metric-summary-value");
      HBox summary = new HBox(6, avgCaption, average, new javafx.scene.layout.Region(), maxCaption, maximum);
      javafx.scene.layout.HBox.setHgrow(summary.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
      javafx.scene.control.Tooltip.install(heading, new javafx.scene.control.Tooltip(descriptions.get(metric)));
      VBox item = new VBox(3, heading, value, summary);
      item.getStyleClass().add("blood-pressure-metric-item");
      if ("stop counters".equals(metric)) item.getStyleClass().add("blood-pressure-stop-counters-item");
      metricCards.put(metric, item);
      liveMetrics.getChildren().add(item);
      boolean visible = telemetryService.visibleMetrics().contains(metric);
      item.setVisible(visible); item.setManaged(visible);
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
    Label liveHint = new Label("Values appear when Knightshade is thinking. Ponder metrics update after the opponent replies; enable Ponder in Settings → Knightshade.");
    liveHint.getStyleClass().add("hero-support");
    javafx.scene.control.Button export = new javafx.scene.control.Button("Export CSV");
    export.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    export.setOnAction(event -> exportCsv());
    export.disableProperty().bind(active.not());
    javafx.scene.control.Button exportSummary = new javafx.scene.control.Button("Export search summary");
    exportSummary.getStyleClass().addAll("message-box-button", "message-box-accept-button");
    exportSummary.setOnAction(event -> exportSearchSummaryCsv());
    exportSummary.disableProperty().bind(active.not());
    HBox actions = new HBox(10, metricsToggle, export, exportSummary);
    actions.setAlignment(Pos.CENTER_LEFT);
    Label chartsTitle = new Label("Search progress");
    chartsTitle.getStyleClass().add("card-title");
    javafx.scene.control.ScrollPane metricsScroll = new javafx.scene.control.ScrollPane(liveMetrics);
    metricsScroll.setFitToWidth(true);
    metricsScroll.setMinHeight(0);
    metricsScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
    metricsScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
    metricsScroll.getStyleClass().add("blood-pressure-metrics-scroll");
    VBox.setVgrow(metricsScroll, javafx.scene.layout.Priority.ALWAYS);
    VBox chartsPane = new VBox(10, chartsTitle, mainNodesChart, depthChart, npsChart);
    chartsPane.getStyleClass().add("blood-pressure-charts-pane");
    chartsPane.setMaxWidth(Double.MAX_VALUE);
    VBox metricsPane = new VBox(8, liveTitle, liveHint, metricsScroll);
    metricsPane.getStyleClass().add("blood-pressure-metrics-pane");
    VBox.setVgrow(metricsPane, javafx.scene.layout.Priority.ALWAYS);
    root.getChildren().addAll(title, subtitle, actions, new Separator(), chartsPane, metricsPane);
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
          String csv = KnightshadeTelemetryCsvExporter.toCsv(samples,
              telemetryService.visibleMetrics(), telemetryService);
          try { Files.writeString(file.toPath(), csv, StandardCharsets.UTF_8); }
          catch (IOException ignored) { }
        });
  }

  /** Exports one terminal summary per search so cumulative iteration snapshots are never summed twice. */
  private void exportSearchSummaryCsv() {
    List<SearchSummary> summaries = searchSummaries(telemetryService.samples()).stream()
        .filter(summary -> summary.terminal() != null)
        .toList();
    if (summaries.isEmpty() || monitorStage == null) return;
    fileChooserFactory.chooseCsvExportFile(monitorStage, "knightshade-search-summary")
        .ifPresent(file -> {
          StringBuilder csv = new StringBuilder(
              "gameId,searchId,searchStartedAt,sideToMove,fullmoveNumber,engineVersion,engineBuild,limits,requestedWorkers,activeWorkers,lastCompletedDepth,timeToDepthMillis,nodesAtLastCompletedDepth,postDepthTimeMillis,postDepthNodes,terminalDepth,terminalScore,scoreType,bestMove,totalElapsedMillis,totalNodes,stopReason,ponderHits,ponderMisses,ponderDecisions,ponderHitRate,ponderReusedDepthAvg,ponderReusedDepthMax,ponderStarts\n");
          for (SearchSummary summary : summaries) {
            SearchTelemetrySnapshot terminal = summary.terminal();
            SearchTelemetrySnapshot iteration = summary.lastCompletedIteration();
            var context = terminal.context();
            appendCsvRow(csv, List.of(
                context.gameId(), context.searchId(), context.startedAt(), context.sideToMove(),
                context.fullmoveNumber(), context.engineVersion(), context.engineBuild(), context.limits(), terminal.requestedWorkers(),
                terminal.activeWorkers(), iteration == null ? "" : iteration.depth(),
                iteration == null ? "" : iteration.elapsedMillis(),
                iteration == null ? "" : totalNodes(iteration),
                summary.postDepthTimeMillis() == null ? "" : summary.postDepthTimeMillis(),
                summary.postDepthNodes() == null ? "" : summary.postDepthNodes(), terminal.depth(),
                terminal.score(), terminal.isMateScore() ? "MATE" : "CENTIPAWNS",
                terminal.bestMove() == null ? "" : terminal.bestMove().toUci(), terminal.elapsedMillis(),
                totalNodes(terminal), terminal.stopReason(), telemetryService.ponderHits(),
                telemetryService.ponderMisses(), telemetryService.ponderDecisions(),
                formatRate(telemetryService.ponderHitRate()), formatAverageDepth(), formatMaxDepth(),
                telemetryService.ponderStarts()));
          }
          try { Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8); }
          catch (IOException ignored) { }
        });
  }

  private static void appendCsvRow(StringBuilder csv, List<?> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) csv.append(',');
      csv.append(csvValue(values.get(i)));
    }
    csv.append('\n');
  }

  private static String csvValue(Object value) {
    String text = value == null ? "" : String.valueOf(value);
    return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
  }

  private void receiveSnapshot(SearchTelemetrySnapshot snapshot) {
    if (telemetryService.sessionStartedAt() != null && !telemetryService.sessionStartedAt().equals(sessionStartedAt)) {
      maxima.clear();
      averages.clear();
      aggregatedSnapshots.clear();
      sessionStartedAt = telemetryService.sessionStartedAt();
    }
    latestSnapshot = snapshot;
    refreshCoalescer.offer(snapshot, Platform::runLater, this::flushSnapshot);
  }

  private void flushSnapshot() {
    if (mainNodesChart == null || depthChart == null || npsChart == null) {
      refreshCoalescer.finished(refreshCoalescer.latest(), Platform::runLater, this::flushSnapshot);
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
    SearchTelemetrySnapshot snapshot = refreshCoalescer.latest();
    if (snapshot != null) {
      List<SearchTelemetrySnapshot> samples = telemetryService.samples();
      renderSnapshot(snapshot, samples);
      List<SearchTelemetrySnapshot> iterations = samples.stream()
          .filter(s -> s.event() == com.knightshade.engine.api.SearchTelemetryEvent.ITERATION_COMPLETED)
          .toList();
      mainNodesChart.renderTelemetry(iterations.stream().map(SearchTelemetrySnapshot::mainNodes).toList(), "Main nodes");
      depthChart.renderTelemetry(iterations.stream().map(s -> (long) s.depth()).toList(), "Depth");
      npsChart.renderTelemetry(iterations.stream().map(this::nps).toList(), "NPS");
      lastRenderNanos = System.nanoTime();
    }
    refreshCoalescer.finished(snapshot, Platform::runLater, this::flushSnapshot);
  }

  private void renderSnapshot(SearchTelemetrySnapshot snapshot, List<SearchTelemetrySnapshot> samples) {
    for (String metric : METRICS) {
      VBox item = metricCards.get(metric);
      if (item != null) { boolean visible = telemetryService.visibleMetrics().contains(metric); item.setVisible(visible); item.setManaged(visible); }
    }
    if (snapshot.event() == com.knightshade.engine.api.SearchTelemetryEvent.PONDER_STARTED
        || snapshot.event() == com.knightshade.engine.api.SearchTelemetryEvent.PONDER_HIT
        || snapshot.event() == com.knightshade.engine.api.SearchTelemetryEvent.PONDER_MISS) {
      renderPonderMetrics();
      return; // Decision samples must not overwrite the last real-search values with zeroes.
    }
    List<SearchSummary> summaries = searchSummaries(samples);
    SearchKey currentKey = new SearchKey(snapshot.context().gameId(), snapshot.context().searchId());
    SearchSummary currentSummary = summaries.stream()
        .filter(summary -> summary.key().equals(currentKey)).findFirst().orElse(null);
    SearchTelemetrySnapshot lastIteration = currentSummary == null
        ? null : currentSummary.lastCompletedIteration();
    boolean aggregateThisSnapshot = aggregatedSnapshots.add(new SnapshotKey(
        snapshot.context().gameId(), snapshot.context().searchId(), snapshot.event(), snapshot.observedAt()));
    updateSearchSummary("depth", lastIteration == null ? null : (long) lastIteration.depth(), summaries,
        summary -> summary.lastCompletedIteration().depth());
    updateSearchSummary("time to depth (ms)", lastIteration == null ? null : lastIteration.elapsedMillis(), summaries,
        summary -> summary.lastCompletedIteration().elapsedMillis());
    updateSearchSummary("post-depth time (ms)", currentSummary == null ? null : currentSummary.postDepthTimeMillis(),
        summaries, SearchSummary::postDepthTimeMillis);
    updateSearchSummary("post-depth nodes", currentSummary == null ? null : currentSummary.postDepthNodes(),
        summaries, SearchSummary::postDepthNodes);
    update("mainNodes", aggregateThisSnapshot, snapshot.mainNodes()); update("qNodes", aggregateThisSnapshot, snapshot.qNodes());
    update("TT hit / cutoff", aggregateThisSnapshot, snapshot.ttHits(), snapshot.ttCutoffs()); update("beta cutoff", aggregateThisSnapshot, snapshot.betaCutoffs());
    update("PVS re-search", aggregateThisSnapshot, snapshot.pvsResearches()); update("null / LMR", aggregateThisSnapshot, snapshot.nullMoveCutoffs(), snapshot.lmrApplications());
    update("aspiration retries", aggregateThisSnapshot, snapshot.aspirationRetries()); update("mate confirmations", aggregateThisSnapshot, snapshot.mateConfirmations());
    update("evaluation cache", aggregateThisSnapshot, snapshot.evaluationCacheHits(), snapshot.evaluationCacheMisses());
    update("workers", aggregateThisSnapshot, snapshot.activeWorkers(), snapshot.requestedWorkers());
    updateAvailable("qsearch", aggregateThisSnapshot, snapshot.quietnessMetricsAvailable(), snapshot.quiescenceEntries());
    updateAvailable("stand-pat", aggregateThisSnapshot, snapshot.quietnessMetricsAvailable(), snapshot.standPatCutoffs(), snapshot.stalemateChecks());
    updateAvailable("move lists", aggregateThisSnapshot, snapshot.quietnessMetricsAvailable(), snapshot.moveListsGenerated());
    updateAvailable("quiet checks", aggregateThisSnapshot, snapshot.quietnessMetricsAvailable(), snapshot.quietChecksExamined());
    updateAvailable("SEE", aggregateThisSnapshot, snapshot.quietnessMetricsAvailable(), snapshot.seeEvaluations(), snapshot.seePrunes());
    updateText("search", snapshot.context().gameId() + " · " + snapshot.context().searchId().substring(0, 8)
        + " · " + (snapshot.isMateScore() ? "MATE" : "CP"));
    update("NPS", aggregateThisSnapshot, nps(snapshot)); updateText("stopReason", snapshot.event() + " · " + snapshot.stopReason());
    updateText("stop counters", "TIME_LIMIT " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.TIME_LIMIT, 0L)
        + " / DEPTH " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.DEPTH_LIMIT, 0L)
        + " / MATE " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.MATE, 0L)
        + " / CANCELLED " + telemetryService.stopReasonCounts().getOrDefault(com.knightshade.engine.api.StopReason.CANCELLED, 0L));
    renderPonderMetrics();
  }

  private void renderPonderMetrics() {
    updateText("ponder starts", Long.toString(telemetryService.ponderStarts()));
    long decisions = telemetryService.ponderDecisions();
    var hitRate = telemetryService.ponderHitRate();
    updateText("ponder hit rate", decisions == 0 ? "—" : telemetryService.ponderHits() + " / " + decisions
        + " · " + String.format(java.util.Locale.ROOT, "%.1f%%", hitRate.orElse(0) * 100));
    var lastDepth = telemetryService.lastPonderReusedDepth();
    setValue("ponder reused depth", lastDepth.isPresent() ? lastDepth.getAsInt() : "—");
    setAverageValue("ponder reused depth", formatAverageDepth());
    setMaxValue("ponder reused depth", formatMaxDepth());
  }

  private String formatRate(java.util.OptionalDouble rate) {
    return rate.isPresent() ? String.format(java.util.Locale.ROOT, "%.6f", rate.getAsDouble()) : "";
  }

  private String formatAverageDepth() {
    return telemetryService.ponderReusedDepthAverage().isPresent()
        ? String.format(java.util.Locale.ROOT, "%.2f", telemetryService.ponderReusedDepthAverage().getAsDouble()) : "";
  }

  private String formatMaxDepth() {
    return telemetryService.ponderReusedDepthMax().isPresent()
        ? Integer.toString(telemetryService.ponderReusedDepthMax().getAsInt()) : "";
  }

  /** Derived on the JavaFX side from completed-depth snapshots; never in the search hot path. */
  private long nps(SearchTelemetrySnapshot snapshot) {
    return snapshot.elapsedMillis() <= 0 ? 0L : (snapshot.mainNodes() + snapshot.qNodes()) * 1_000L / snapshot.elapsedMillis();
  }

  private void setValue(String metric, Object value) {
    javafx.scene.control.Label label = liveValues.get(metric);
    if (label != null) label.setText(String.valueOf(value));
  }

  private void update(String metric, boolean aggregate, long... values) {
    StringBuilder current = new StringBuilder(); StringBuilder maximum = new StringBuilder();
    double[] sum = averages.computeIfAbsent(metric, k -> new double[values.length + 1]);
    if (aggregate) sum[0]++;
    for (int i = 0; i < values.length; i++) { long value = values[i]; if (aggregate) sum[i + 1] += value;
      if (current.length() > 0) { current.append(" / "); maximum.append(" / "); }
      current.append(value); String key = metric + "#" + i;
      long max = aggregate ? Math.max(maxima.getOrDefault(key, 0L), value) : maxima.getOrDefault(key, value);
      if (aggregate) maxima.put(key, max);
      maximum.append(max);
    }
    StringBuilder average = new StringBuilder();
    for (int i = 0; i < values.length; i++) { if (i > 0) average.append(" / "); average.append(Math.round(sum[i + 1] / sum[0])); }
    setValue(metric, current); setAverageValue(metric, average); setMaxValue(metric, maximum);
  }
  private void updateAvailable(String metric, boolean aggregate, boolean available, long... values) {
    if (available) {
      update(metric, aggregate, values);
      return;
    }
    setValue(metric, "—");
    setAverageValue(metric, "—");
    setMaxValue(metric, "—");
  }

  private void updateSearchSummary(String metric, Long current, List<SearchSummary> summaries,
      ToLongFunction<SearchSummary> value) {
    setValue(metric, current == null ? "—" : current);
    List<Long> completed = summaries.stream()
        .filter(summary -> summary.terminal() != null && summary.lastCompletedIteration() != null)
        .map(summary -> value.applyAsLong(summary))
        .toList();
    if (completed.isEmpty()) {
      setAverageValue(metric, "—");
      setMaxValue(metric, "—");
      return;
    }
    long total = completed.stream().mapToLong(Long::longValue).sum();
    setAverageValue(metric, Math.round((double) total / completed.size()));
    setMaxValue(metric, completed.stream().mapToLong(Long::longValue).max().orElse(0));
  }

  private static List<SearchSummary> searchSummaries(List<SearchTelemetrySnapshot> samples) {
    Map<SearchKey, SearchSummaryBuilder> grouped = new LinkedHashMap<>();
    for (SearchTelemetrySnapshot sample : samples) {
      if (sample.event() != com.knightshade.engine.api.SearchTelemetryEvent.ITERATION_COMPLETED
          && sample.event() != com.knightshade.engine.api.SearchTelemetryEvent.SEARCH_FINISHED) continue;
      SearchKey key = new SearchKey(sample.context().gameId(), sample.context().searchId());
      SearchSummaryBuilder builder = grouped.computeIfAbsent(key, SearchSummaryBuilder::new);
      if (sample.event() == com.knightshade.engine.api.SearchTelemetryEvent.ITERATION_COMPLETED) {
        builder.lastCompletedIteration = sample;
      } else if (sample.event() == com.knightshade.engine.api.SearchTelemetryEvent.SEARCH_FINISHED) {
        builder.terminal = sample;
      }
    }
    return grouped.values().stream().map(SearchSummaryBuilder::build).toList();
  }

  private static long totalNodes(SearchTelemetrySnapshot snapshot) {
    return snapshot.mainNodes() + snapshot.qNodes();
  }

  private record SearchKey(String gameId, String searchId) {}
  private record SnapshotKey(String gameId, String searchId,
      com.knightshade.engine.api.SearchTelemetryEvent event, Instant observedAt) {}

  private record SearchSummary(SearchKey key, SearchTelemetrySnapshot lastCompletedIteration,
      SearchTelemetrySnapshot terminal, Long postDepthTimeMillis, Long postDepthNodes) {}

  private static final class SearchSummaryBuilder {
    private final SearchKey key;
    private SearchTelemetrySnapshot lastCompletedIteration;
    private SearchTelemetrySnapshot terminal;

    private SearchSummaryBuilder(SearchKey key) { this.key = key; }

    private SearchSummary build() {
      Long postTime = null;
      Long postNodes = null;
      if (terminal != null && lastCompletedIteration != null) {
        postTime = Math.max(0, terminal.elapsedMillis() - lastCompletedIteration.elapsedMillis());
        postNodes = Math.max(0, totalNodes(terminal) - totalNodes(lastCompletedIteration));
      }
      return new SearchSummary(key, lastCompletedIteration, terminal, postTime, postNodes);
    }
  }
  private void updateText(String metric, Object value) { setValue(metric, value); setAverageValue(metric, "—"); setMaxValue(metric, "—"); }
  private void setMaxValue(String metric, Object value) { var label = maxValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
  private void setAverageValue(String metric, Object value) { var label = avgValues.get(metric); if (label != null) label.setText(String.valueOf(value)); }
}
