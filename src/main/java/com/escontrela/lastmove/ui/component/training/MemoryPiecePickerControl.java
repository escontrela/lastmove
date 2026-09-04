package com.escontrela.lastmove.ui.component.training;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** In-screen chooser for reconstructing one hidden square with a piece type and colour. */
public final class MemoryPiecePickerControl extends StackPane {
  private static final List<PieceType> PIECES =
      List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN);
  private static final double IMAGE_SIZE = 52.0;

  private final ObjectProperty<EventHandler<PieceSelectedEvent>> onPieceSelected =
      new SimpleObjectProperty<>(this, "onPieceSelected");
  private final ObjectProperty<EventHandler<ActionEvent>> onCancel =
      new SimpleObjectProperty<>(this, "onCancel");
  private Button firstChoice;

  public MemoryPiecePickerControl() {
    getStyleClass().addAll("message-box-overlay", "memory-piece-picker-overlay");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Label title = new Label("Which piece was here?");
    title.getStyleClass().add("promotion-picker-title");
    Button close = new Button("×");
    close.setAccessibleText("Cancel piece selection");
    close.getStyleClass().add("memory-piece-picker-close");
    close.setOnAction(event -> cancel());
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    HBox heading = new HBox(10, title, spacer, close);
    heading.setAlignment(Pos.CENTER_LEFT);

    VBox rows = new VBox(8, createRow(PieceColor.WHITE), createRow(PieceColor.BLACK));
    VBox card = new VBox(14, heading, rows);
    card.setPadding(new Insets(18));
    card.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    card.getStyleClass().addAll("promotion-picker-card", "memory-piece-picker-card");
    getChildren().add(card);

    visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
    addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
      if (event.getTarget() == this) { cancel(); event.consume(); }
    });
    setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) { cancel(); event.consume(); }
    });
    hide();
  }

  private HBox createRow(PieceColor color) {
    HBox row = new HBox(8);
    row.setAlignment(Pos.CENTER);
    row.getStyleClass().add("memory-piece-picker-row");
    for (PieceType type : PIECES) {
      Button choice = new Button();
      choice.setAccessibleText("Choose " + color.name().toLowerCase(Locale.ROOT) + " " + type.name().toLowerCase(Locale.ROOT));
      choice.setFocusTraversable(true);
      choice.getStyleClass().add("promotion-picker-choice");
      ImageView image = new ImageView(pieceImage(type, color));
      image.setFitWidth(IMAGE_SIZE);
      image.setFitHeight(IMAGE_SIZE);
      image.setPreserveRatio(true);
      image.setMouseTransparent(true);
      choice.setGraphic(image);
      choice.setOnAction(event -> select(type, color));
      if (firstChoice == null) firstChoice = choice;
      row.getChildren().add(choice);
    }
    return row;
  }

  private Image pieceImage(PieceType type, PieceColor color) {
    String path = "/chess-pieces/" + color.name().toLowerCase(Locale.ROOT) + "-" + type.name().toLowerCase(Locale.ROOT) + ".png";
    URL resource = MemoryPiecePickerControl.class.getResource(path);
    if (resource == null) throw new IllegalStateException("Missing memory piece resource " + path);
    return new Image(resource.toExternalForm());
  }

  public void showPicker() {
    setManaged(true);
    setVisible(true);
    toFront();
    Platform.runLater(firstChoice::requestFocus);
  }

  public void hide() { setVisible(false); setManaged(false); }

  private void select(PieceType type, PieceColor color) {
    hide();
    EventHandler<PieceSelectedEvent> handler = getOnPieceSelected();
    if (handler != null) handler.handle(new PieceSelectedEvent(this, type, color));
  }

  private void cancel() {
    hide();
    EventHandler<ActionEvent> handler = getOnCancel();
    if (handler != null) handler.handle(new ActionEvent(this, this));
  }

  public EventHandler<PieceSelectedEvent> getOnPieceSelected() { return onPieceSelected.get(); }
  public void setOnPieceSelected(EventHandler<PieceSelectedEvent> handler) { onPieceSelected.set(handler); }
  public EventHandler<ActionEvent> getOnCancel() { return onCancel.get(); }
  public void setOnCancel(EventHandler<ActionEvent> handler) { onCancel.set(handler); }

  public static final class PieceSelectedEvent extends Event {
    public static final EventType<PieceSelectedEvent> PIECE_SELECTED = new EventType<>(Event.ANY, "MEMORY_PIECE_SELECTED");
    private final PieceType type;
    private final PieceColor color;

    private PieceSelectedEvent(MemoryPiecePickerControl source, PieceType type, PieceColor color) {
      super(source, source, PIECE_SELECTED);
      this.type = Objects.requireNonNull(type);
      this.color = Objects.requireNonNull(color);
    }

    public PieceType pieceType() { return type; }
    public PieceColor pieceColor() { return color; }
  }
}
