package com.escontrela.lastmove.ui.component.arena;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.Cursor;

/** Reusable, horizontally scrollable win/loss timeline for dated game activity. */
public final class GameTimelineControl extends VBox {
  public enum Outcome { WON, LOST, DRAWN, IN_PROGRESS }
  public record Entry(Instant playedAt, String opponent, String rating, Outcome outcome, Runnable onActivate) {
    public Entry(Instant playedAt, String opponent, String rating, Outcome outcome) { this(playedAt, opponent, rating, outcome, null); }
    public Entry { Objects.requireNonNull(playedAt); opponent = Objects.requireNonNullElse(opponent, "Opponent"); rating = Objects.requireNonNullElse(rating, ""); Objects.requireNonNull(outcome); }
  }
  private enum Range { LAST_24_HOURS("Last 24 hours", Duration.ofHours(24)), LAST_7_DAYS("Last 7 days", Duration.ofDays(7)), LAST_30_DAYS("Last 30 days", Duration.ofDays(30)), ALL("All activity", null);
    final String label; final Duration duration; Range(String label, Duration duration) { this.label = label; this.duration = duration; } @Override public String toString() { return label; } }

  private static final double STEP = 150;
  private final ComboBox<Range> range = new ComboBox<>(FXCollections.observableArrayList(Range.values()));
  private final Pane lane = new Pane();
  private final Label empty = new Label("No games in this period yet.");
  private List<Entry> entries = List.of();
  private double panStartX, panStartH;
  private boolean panning;

  public GameTimelineControl() {
    getStyleClass().add("arena-game-timeline"); setSpacing(8);
    Label title = new Label("Game timeline"); title.getStyleClass().add("sessions-section-title");
    range.setValue(Range.LAST_24_HOURS); range.getStyleClass().add("arena-timeline-range"); range.valueProperty().addListener((o, old, value) -> render());
    VBox heading = new VBox(2, title); HBox.setHgrow(heading, Priority.ALWAYS);
    HBox toolbar = new HBox(10, heading, range); toolbar.setAlignment(Pos.CENTER_LEFT);
    lane.getStyleClass().add("arena-timeline-lane"); lane.setMinHeight(260); lane.setPrefHeight(260);
    ScrollPane scroll = new ScrollPane(lane); scroll.setFitToHeight(true); scroll.setFitToWidth(false); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("arena-timeline-scroll"); configurePan(scroll);
    empty.getStyleClass().add("session-empty-state"); empty.setPadding(new Insets(30, 0, 30, 0));
    getChildren().addAll(toolbar, scroll, empty); render();
  }

  public void setEntries(List<Entry> values) { entries = values == null ? List.of() : values.stream().sorted(Comparator.comparing(Entry::playedAt)).toList(); render(); }

  private void configurePan(ScrollPane scroll) {
    scroll.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> { if (event.getButton() != MouseButton.SECONDARY) return; panStartX = event.getSceneX(); panStartH = scroll.getHvalue(); panning = true; scroll.setCursor(Cursor.CLOSED_HAND); event.consume(); });
    scroll.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> { if (!panning) return; double range = Math.max(1, lane.getBoundsInParent().getWidth() - scroll.getViewportBounds().getWidth()); scroll.setHvalue(Math.clamp(panStartH - (event.getSceneX() - panStartX) / range, 0, 1)); event.consume(); });
    scroll.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> { if (event.getButton() == MouseButton.SECONDARY) { panning = false; scroll.setCursor(Cursor.DEFAULT); event.consume(); } });
  }

  private void render() {
    if (lane == null || range.getValue() == null) return;
    Instant threshold = range.getValue().duration == null ? Instant.MIN : Instant.now().minus(range.getValue().duration);
    List<Entry> visible = entries.stream().filter(entry -> !entry.playedAt().isBefore(threshold)).toList();
    lane.getChildren().clear(); empty.setVisible(visible.isEmpty()); empty.setManaged(visible.isEmpty());
    double width = Math.max(680, visible.size() * STEP + 48); lane.setMinWidth(width); lane.setPrefWidth(width);
    javafx.scene.shape.Line baseline = new javafx.scene.shape.Line(20, 121, width - 20, 121); baseline.getStyleClass().add("arena-timeline-baseline"); lane.getChildren().add(baseline);
    for (int index = 0; index < visible.size(); index++) addMarker(visible.get(index), 48 + index * STEP);
  }

  private void addMarker(Entry entry, double x) {
    boolean won = entry.outcome() == Outcome.WON, lost = entry.outcome() == Outcome.LOST;
    double endY = won ? 76 : lost ? 171 : 121;
    javafx.scene.shape.Line stem = new javafx.scene.shape.Line(x, 121, x, endY); stem.getStyleClass().addAll("arena-timeline-stem", won ? "arena-timeline-win" : lost ? "arena-timeline-loss" : "arena-timeline-neutral");
    Label detail = new Label(entry.opponent()); detail.getStyleClass().addAll("arena-timeline-detail", won ? "arena-timeline-detail-win" : lost ? "arena-timeline-detail-loss" : "arena-timeline-detail-neutral"); detail.setWrapText(true); detail.setMaxWidth(108); detail.setPrefWidth(108); detail.setAlignment(Pos.CENTER); detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
    double labelX = x - 52;
    detail.relocate(labelX, won ? 20 : 181);
    Label time = new Label(DateTimeFormatter.ofPattern("dd MMM · HH:mm").withZone(ZoneId.systemDefault()).format(entry.playedAt())); time.getStyleClass().add("arena-timeline-time"); time.setPrefWidth(108); time.setAlignment(Pos.CENTER); time.relocate(labelX, won ? 47 : 207);
    if (entry.onActivate() != null) {
      detail.setCursor(Cursor.HAND); detail.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> { if (event.getButton() == MouseButton.PRIMARY) entry.onActivate().run(); });
      detail.setOnMouseEntered(event -> detail.setOpacity(0.72)); detail.setOnMouseExited(event -> detail.setOpacity(1.0));
    }
    lane.getChildren().addAll(stem, detail, time);
  }
}
