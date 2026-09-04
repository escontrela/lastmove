package com.escontrela.lastmove.ui.component.arena;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.TextAlignment;

/** Reusable, horizontally scrollable win/loss timeline for dated game activity. */
public final class GameTimelineControl extends VBox {
  public enum Outcome {
    WON,
    LOST,
    DRAWN,
    IN_PROGRESS
  }

  public record Entry(
      Instant playedAt, String opponent, String rating, Outcome outcome, Runnable onActivate) {
    public Entry(Instant playedAt, String opponent, String rating, Outcome outcome) {
      this(playedAt, opponent, rating, outcome, null);
    }

    public Entry {
      Objects.requireNonNull(playedAt);
      opponent = Objects.requireNonNullElse(opponent, "Opponent");
      rating = Objects.requireNonNullElse(rating, "");
      Objects.requireNonNull(outcome);
    }
  }

  private enum Range {
    LAST_24_HOURS("Last 24 hours", Duration.ofHours(24)),
    LAST_7_DAYS("Last 7 days", Duration.ofDays(7)),
    LAST_30_DAYS("Last 30 days", Duration.ofDays(30)),
    ALL("All activity", null);
    final String label;
    final Duration duration;

    Range(String label, Duration duration) {
      this.label = label;
      this.duration = duration;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final double STEP = 250;
  private static final double CARD_WIDTH = 224;
  private static final double BASELINE_Y = 32;
  private final ComboBox<Range> range =
      new ComboBox<>(FXCollections.observableArrayList(Range.values()));
  private final Pane lane = new Pane();
  private final Label empty = new Label("No games in this period yet.");
  private final ImageView overviewIcon = new ImageView();
  private final ListChangeListener<String> themeListener = change -> refreshOverviewIcon();
  private Parent observedRoot;
  private List<Entry> entries = List.of();
  private double panStartX, panStartH;
  private boolean panning;

  public GameTimelineControl() {
    getStyleClass().add("arena-game-timeline");
    setSpacing(8);
    Label title = new Label("Arena overview");
    title.getStyleClass().add("sessions-section-title");
    overviewIcon.setFitWidth(24); overviewIcon.setFitHeight(24); overviewIcon.setPreserveRatio(true);
    title.setGraphic(overviewIcon); title.setGraphicTextGap(9);
    range.setValue(Range.LAST_24_HOURS);
    range.getStyleClass().add("arena-timeline-range");
    range.valueProperty().addListener((o, old, value) -> render());
    VBox heading = new VBox(2, title);
    HBox.setHgrow(heading, Priority.ALWAYS);
    HBox toolbar = new HBox(10, heading, range);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    lane.getStyleClass().add("arena-timeline-lane");
    lane.setMinHeight(232);
    lane.setPrefHeight(232);
    ScrollPane scroll = new ScrollPane(lane);
    scroll.setFitToHeight(true);
    scroll.setFitToWidth(false);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.getStyleClass().add("arena-timeline-scroll");
    configurePan(scroll);
    empty.getStyleClass().add("session-empty-state");
    empty.setPadding(new Insets(30, 0, 30, 0));
    getChildren().addAll(toolbar, scroll, empty);
    sceneProperty().addListener((ignored, oldScene, scene) -> observeTheme(scene == null ? null : scene.getRoot()));
    render();
  }

  private void observeTheme(Parent root) {
    if (observedRoot != null) observedRoot.getStyleClass().removeListener(themeListener);
    observedRoot = root;
    if (root != null) observedRoot.getStyleClass().addListener(themeListener);
    refreshOverviewIcon();
  }

  private void refreshOverviewIcon() {
    boolean nightMode = observedRoot != null && observedRoot.getStyleClass().contains("night-mode");
    String resource = nightMode ? "/images/monitoring_35dp_FFFFFF.png" : "/images/monitoring_35dp_000000.png";
    overviewIcon.setImage(new Image(Objects.requireNonNull(getClass().getResource(resource)).toExternalForm()));
  }

  public void setEntries(List<Entry> values) {
    entries =
        values == null
            ? List.of()
            : values.stream().sorted(Comparator.comparing(Entry::playedAt)).toList();
    render();
  }

  private void configurePan(ScrollPane scroll) {
    scroll.addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        event -> {
          if (event.getButton() != MouseButton.SECONDARY) return;
          panStartX = event.getSceneX();
          panStartH = scroll.getHvalue();
          panning = true;
          scroll.setCursor(Cursor.CLOSED_HAND);
          event.consume();
        });
    scroll.addEventFilter(
        MouseEvent.MOUSE_DRAGGED,
        event -> {
          if (!panning) return;
          double range =
              Math.max(
                  1, lane.getBoundsInParent().getWidth() - scroll.getViewportBounds().getWidth());
          scroll.setHvalue(Math.clamp(panStartH - (event.getSceneX() - panStartX) / range, 0, 1));
          event.consume();
        });
    scroll.addEventFilter(
        MouseEvent.MOUSE_RELEASED,
        event -> {
          if (event.getButton() == MouseButton.SECONDARY) {
            panning = false;
            scroll.setCursor(Cursor.DEFAULT);
            event.consume();
          }
        });
  }

  private void render() {
    if (lane == null || range.getValue() == null) return;
    Instant threshold =
        range.getValue().duration == null
            ? Instant.MIN
            : Instant.now().minus(range.getValue().duration);
    List<Entry> visible =
        entries.stream().filter(entry -> !entry.playedAt().isBefore(threshold)).toList();
    lane.getChildren().clear();
    empty.setVisible(visible.isEmpty());
    empty.setManaged(visible.isEmpty());
    double width = Math.max(680, visible.size() * STEP + 52);
    lane.setMinWidth(width);
    lane.setPrefWidth(width);
    Line baseline = new Line(22, BASELINE_Y, width - 22, BASELINE_Y);
    baseline.getStyleClass().add("arena-timeline-baseline");
    lane.getChildren().add(baseline);
    for (int index = 0; index < visible.size(); index++) {
      double x = 34 + index * STEP;
      if (index > 0) {
        Line segment = new Line(x - STEP + 18, BASELINE_Y, x - 18, BASELINE_Y);
        segment
            .getStyleClass()
            .addAll("arena-timeline-segment", outcomeClass(visible.get(index - 1)));
        lane.getChildren().add(segment);
      }
      addMarker(visible.get(index), x, index == visible.size() - 1);
    }
  }

  private void addMarker(Entry entry, double x, boolean current) {
    boolean won = entry.outcome() == Outcome.WON, lost = entry.outcome() == Outcome.LOST;
    String outcomeClass = outcomeClass(entry);
    Line stem = new Line(x, BASELINE_Y, x, BASELINE_Y + 12);
    stem.getStyleClass().addAll("arena-timeline-stem", outcomeClass);
    Circle node = new Circle(x, BASELINE_Y, 5);
    node.getStyleClass().addAll("arena-timeline-node", outcomeClass);
    VBox card = new VBox(6);
    card.getStyleClass().addAll("arena-timeline-card", outcomeClass);
    if (current) card.getStyleClass().add("arena-timeline-current");
    card.setPrefWidth(CARD_WIDTH);
    card.setMaxWidth(CARD_WIDTH);
    card.setPadding(new Insets(10, 12, 10, 12));
    ImageView botIcon =
        new ImageView(
            new Image(
                Objects.requireNonNull(
                        getClass()
                            .getResource(
                                won ? "/images/bot-win-icon.png" : "/images/bot-lose-icon.png"))
                    .toExternalForm()));
    botIcon.setFitWidth(42);
    botIcon.setFitHeight(42);
    botIcon.setPreserveRatio(true);
    Label detail = new Label(entry.opponent());
    detail.getStyleClass().add("arena-timeline-detail");
    detail.setWrapText(true);
    detail.setMaxWidth(142);
    detail.setAlignment(Pos.CENTER_LEFT);
    detail.setTextAlignment(TextAlignment.LEFT);
    Label time =
        new Label(
            DateTimeFormatter.ofPattern("dd MMM · HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(entry.playedAt()));
    time.getStyleClass().add("arena-timeline-time");
    HBox identity = new HBox(9, botIcon, new VBox(2, detail, time));
    identity.setAlignment(Pos.CENTER_LEFT);
    Label result = new Label(resultText(entry.outcome()));
    result.getStyleClass().addAll("arena-timeline-result", outcomeClass);
    result.setMaxWidth(Double.MAX_VALUE);
    result.setAlignment(Pos.CENTER);
    card.getChildren().addAll(identity, result);
    card.relocate(x - CARD_WIDTH / 2, BASELINE_Y + 18);
    if (current) {
      Label currentLabel = new Label("CURRENT");
      currentLabel.getStyleClass().add("arena-timeline-current-label");
      currentLabel.relocate(x + 10, 2);
      lane.getChildren().add(currentLabel);
    }
    if (entry.onActivate() != null) {
      card.setCursor(Cursor.HAND);
      card.addEventHandler(
          MouseEvent.MOUSE_CLICKED,
          event -> {
            if (event.getButton() == MouseButton.PRIMARY) entry.onActivate().run();
          });
      card.setOnMouseEntered(event -> card.getStyleClass().add("arena-timeline-hover"));
      card.setOnMouseExited(event -> card.getStyleClass().remove("arena-timeline-hover"));
    }
    lane.getChildren().addAll(stem, node, card);
  }

  private static String outcomeClass(Entry entry) {
    return outcomeClass(entry.outcome());
  }

  private static String outcomeClass(Outcome outcome) {
    return switch (outcome) {
      case WON -> "arena-timeline-win";
      case LOST -> "arena-timeline-loss";
      default -> "arena-timeline-neutral";
    };
  }

  private static String resultText(Outcome outcome) {
    return switch (outcome) {
      case WON -> "WON";
      case LOST -> "LOST";
      case DRAWN -> "DRAW";
      case IN_PROGRESS -> "LIVE";
    };
  }
}
