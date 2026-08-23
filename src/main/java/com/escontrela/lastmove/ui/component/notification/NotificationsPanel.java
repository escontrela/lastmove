package com.escontrela.lastmove.ui.component.notification;

import com.escontrela.lastmove.application.notification.GameNotification;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Modal-style overlay for persistent game notifications, following the speaker-notes visual pattern. */
public final class NotificationsPanel extends StackPane {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM · HH:mm").withZone(ZoneId.systemDefault());
  private final VBox content = new VBox(0);
  private Consumer<GameNotification> onOpen = ignored -> {};
  private Consumer<GameNotification> onDelete = ignored -> {};
  private Runnable onClear = () -> {};

  public NotificationsPanel() {
    getStyleClass().add("notifications-overlay"); setAlignment(Pos.CENTER_RIGHT);
    Label title = new Label("Notifications"); title.getStyleClass().add("notifications-title");
    Button close = new Button("×"); close.getStyleClass().add("notifications-close"); close.setOnAction(e -> hide());
    Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox header = new HBox(10, title, spacer, close); header.setAlignment(Pos.CENTER_LEFT);
    content.getStyleClass().add("notifications-content");
    ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("notifications-scroll"); VBox.setVgrow(scroll, Priority.ALWAYS);
    Button clear = new Button("Clear all"); clear.getStyleClass().add("notifications-clear"); clear.setOnAction(e -> onClear.run());
    VBox card = new VBox(16, header, scroll, clear); card.getStyleClass().add("notifications-card"); card.setPadding(new Insets(24)); card.setPrefWidth(440); card.setMaxWidth(440); card.setMaxHeight(Double.MAX_VALUE); card.prefHeightProperty().bind(heightProperty());
    getChildren().add(card); hide();
  }
  public void setNotifications(List<NotificationEntry> notifications) {
    Objects.requireNonNull(notifications); content.getChildren().clear();
    if (notifications.isEmpty()) { Label empty = new Label("You have no game notifications."); empty.getStyleClass().add("notifications-empty"); content.getChildren().add(empty); return; }
    notifications.forEach(notification -> content.getChildren().add(row(notification)));
  }
  private VBox row(NotificationEntry entry) {
    GameNotification notification = entry.notification();
    Label heading = new Label(entry.players()); heading.getStyleClass().add("notifications-heading");
    String event = switch (notification.kind()) { case "GAME_FINISHED" -> "Finished"; case "OPPONENT_MOVED" -> "Opponent moved"; default -> "In progress"; };
    Label detail = new Label(event + " · " + entry.outcome() + " · " + DATE.format(notification.createdAt())); detail.setWrapText(true); detail.getStyleClass().add("notifications-detail");
    Button open = new Button(entry.actionLabel()); open.getStyleClass().add("notifications-open"); open.setOnAction(e -> onOpen.accept(notification));
    Button delete = new Button("Delete"); delete.getStyleClass().add("notifications-delete"); delete.setOnAction(e -> onDelete.accept(notification));
    HBox actions = new HBox(8, open, delete); VBox row = new VBox(8, heading, detail, actions); row.getStyleClass().add("notifications-row"); return row;
  }
  public void setOnOpen(Consumer<GameNotification> handler) { onOpen = Objects.requireNonNull(handler); }
  public void setOnDelete(Consumer<GameNotification> handler) { onDelete = Objects.requireNonNull(handler); }
  public void setOnClear(Runnable handler) { onClear = Objects.requireNonNull(handler); }
  public void show() { setManaged(true); setVisible(true); toFront(); }
  public void hide() { setManaged(false); setVisible(false); }
  public boolean isShowing() { return isVisible(); }
  public record NotificationEntry(GameNotification notification, String players, String outcome, String actionLabel) {
    public NotificationEntry { Objects.requireNonNull(notification); players = Objects.requireNonNullElse(players, "Saved game"); outcome = Objects.requireNonNullElse(outcome, ""); actionLabel = Objects.requireNonNullElse(actionLabel, "Open"); }
  }
}
