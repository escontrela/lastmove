package com.escontrela.lastmove.ui.component.date;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

/** Compact date/time summary that can act as a configurable navigation link. */
public final class DateTimeLinkControl extends HBox {
    public enum DisplayMode { SHORT, LONG }

    private final Label monthLabel = new Label();
    private final Label dayLabel = new Label();
    private final Label detailLabel = new Label();
    private final Label detailDateLabel = new Label();
    private final Button linkButton = new Button("›");
    private final Timeline clock = new Timeline(new KeyFrame(Duration.seconds(30), event -> refresh()));
    private DisplayMode displayMode = DisplayMode.LONG;

    public DateTimeLinkControl() {
        getStyleClass().add("date-time-link-control");
        setAlignment(Pos.CENTER_LEFT);
        monthLabel.getStyleClass().add("date-time-link-month");
        dayLabel.getStyleClass().add("date-time-link-day");
        detailLabel.getStyleClass().add("date-time-link-detail");
        detailDateLabel.getStyleClass().add("date-time-link-detail-date");
        linkButton.getStyleClass().add("date-time-link-arrow");
        linkButton.setFocusTraversable(false);
        linkButton.setOnAction(this::fireLink);
        VBox date = new VBox(0, monthLabel, dayLabel);
        date.setAlignment(Pos.CENTER);
        date.getStyleClass().add("date-time-link-date");
        VBox detail = new VBox(2, detailLabel, detailDateLabel);
        detail.setAlignment(Pos.CENTER_LEFT);
        HBox text = new HBox(12, date, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        // The whole surface is the link; no trailing icon is needed.
        linkButton.setVisible(false);
        linkButton.setManaged(false);
        getChildren().add(text);
        setOnMouseClicked(event -> linkButton.fire());
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        refresh();
    }

    public void setOnLink(EventHandler<ActionEvent> handler) { linkButton.setOnAction(handler); }
    public void setDisplayMode(DisplayMode mode) { displayMode = mode == null ? DisplayMode.LONG : mode; refresh(); }

    private void fireLink(ActionEvent event) { }

    private void refresh() {
        LocalDateTime now = LocalDateTime.now();
        monthLabel.setText(now.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toLowerCase(Locale.ENGLISH));
        dayLabel.setText(now.format(DateTimeFormatter.ofPattern("d", Locale.ENGLISH)));
        if (displayMode == DisplayMode.SHORT) {
            detailLabel.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
            detailDateLabel.setText("");
        } else {
            detailLabel.setText("Today");
            detailDateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)));
        }
    }
}
