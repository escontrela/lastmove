package com.escontrela.lastmove.ui.component.promotion;

import com.escontrela.lastmove.domain.common.PieceColor;
import com.escontrela.lastmove.domain.common.PieceType;
import java.net.URL;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/**
 * Reusable in-screen overlay for choosing the piece created by a pawn promotion.
 *
 * <p>The control is presentation-only: it offers the four legal target piece types for a supplied
 * colour and reports the user's selection without constructing or validating a chess move. Place
 * it as the last child of a screen {@link StackPane}; analysis and progressive-game screens can
 * therefore share the same selector and decide independently how to submit the resulting command.
 */
public final class PromotionPickerControl extends StackPane {

  private static final List<PieceType> PROMOTION_PIECES =
      List.of(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT);
  private static final double PIECE_IMAGE_SIZE = 56.0;

  private final ObjectProperty<PieceColor> promotingColor =
      new SimpleObjectProperty<>(this, "promotingColor", PieceColor.WHITE);
  private final ObjectProperty<EventHandler<PromotionSelectedEvent>> onPromotionSelected =
      new SimpleObjectProperty<>(this, "onPromotionSelected");
  private final ObjectProperty<EventHandler<ActionEvent>> onCancel =
      new SimpleObjectProperty<>(this, "onCancel");

  private final Map<PieceType, Button> choiceButtons = new EnumMap<>(PieceType.class);
  private final VBox card = new VBox(14);

  public PromotionPickerControl() {
    initialiseView();
    initialiseBehaviour();
  }

  private void initialiseView() {
    getStyleClass().addAll("message-box-overlay", "promotion-picker-overlay");
    setAlignment(Pos.CENTER);
    setPickOnBounds(true);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    Label title = new Label("Choose promotion");
    title.getStyleClass().add("promotion-picker-title");

    Label description = new Label("Select the piece for the promoted pawn.");
    description.getStyleClass().add("promotion-picker-description");

    HBox choices = new HBox(10);
    choices.setAlignment(Pos.CENTER);
    choices.getStyleClass().add("promotion-picker-choices");
    for (PieceType pieceType : PROMOTION_PIECES) {
      Button choice = createChoiceButton(pieceType);
      choiceButtons.put(pieceType, choice);
      choices.getChildren().add(choice);
    }

    card.getChildren().addAll(title, description, choices);
    card.setAlignment(Pos.CENTER);
    card.setPadding(new Insets(22));
    card.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    card.getStyleClass().add("promotion-picker-card");
    getChildren().add(card);

    setVisible(false);
    setManaged(false);
  }

  private Button createChoiceButton(PieceType pieceType) {
    Button choice = new Button();
    choice.setAccessibleText("Promote to " + displayName(pieceType));
    choice.setFocusTraversable(true);
    choice.getStyleClass().add("promotion-picker-choice");
    choice.setOnAction(event -> select(pieceType));
    updateChoiceGraphic(choice, pieceType, getPromotingColor());
    return choice;
  }

  private void initialiseBehaviour() {
    promotingColor.addListener(
        (ignored, oldColor, newColor) ->
            choiceButtons.forEach((pieceType, choice) -> updateChoiceGraphic(choice, pieceType, newColor)));
    visibleProperty().addListener((ignored, oldValue, visible) -> setManaged(visible));
    addEventFilter(
        MouseEvent.MOUSE_CLICKED,
        event -> {
          if (event.getTarget() == this) {
            cancel();
            event.consume();
          }
        });
    setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            cancel();
            event.consume();
          }
        });
  }

  private void updateChoiceGraphic(Button choice, PieceType pieceType, PieceColor color) {
    ImageView graphic = new ImageView(pieceImage(pieceType, color));
    graphic.setFitWidth(PIECE_IMAGE_SIZE);
    graphic.setFitHeight(PIECE_IMAGE_SIZE);
    graphic.setPreserveRatio(true);
    graphic.setMouseTransparent(true);
    choice.setGraphic(graphic);
  }

  private Image pieceImage(PieceType pieceType, PieceColor color) {
    String resourcePath =
        "/chess-pieces/"
            + color.name().toLowerCase(Locale.ROOT)
            + "-"
            + pieceType.name().toLowerCase(Locale.ROOT)
            + ".png";
    URL resource = PromotionPickerControl.class.getResource(resourcePath);
    if (resource == null) {
      throw new IllegalStateException("Missing promotion piece resource " + resourcePath);
    }
    return new Image(resource.toExternalForm());
  }

  private void select(PieceType pieceType) {
    hide();
    EventHandler<PromotionSelectedEvent> handler = getOnPromotionSelected();
    if (handler != null) {
      handler.handle(new PromotionSelectedEvent(this, pieceType));
    }
  }

  private void cancel() {
    hide();
    EventHandler<ActionEvent> handler = getOnCancel();
    if (handler != null) {
      handler.handle(new ActionEvent(this, this));
    }
  }

  /** Displays the picker for the supplied pawn colour and focuses the first choice. */
  public void showFor(PieceColor color) {
    setPromotingColor(Objects.requireNonNull(color, "color must not be null"));
    setManaged(true);
    setVisible(true);
    toFront();
    Platform.runLater(() -> choiceButtons.get(PieceType.QUEEN).requestFocus());
  }

  /** Hides the picker without reporting either a selection or cancellation. */
  public void hide() {
    setVisible(false);
    setManaged(false);
  }

  /** Returns the ordered promotion options used by every picker instance. */
  public static List<PieceType> supportedPromotionPieces() {
    return PROMOTION_PIECES;
  }

  public final ObjectProperty<PieceColor> promotingColorProperty() {
    return promotingColor;
  }

  public final PieceColor getPromotingColor() {
    return promotingColor.get();
  }

  public final void setPromotingColor(PieceColor color) {
    promotingColor.set(Objects.requireNonNull(color, "color must not be null"));
  }

  public final ObjectProperty<EventHandler<PromotionSelectedEvent>> onPromotionSelectedProperty() {
    return onPromotionSelected;
  }

  public final EventHandler<PromotionSelectedEvent> getOnPromotionSelected() {
    return onPromotionSelected.get();
  }

  public final void setOnPromotionSelected(EventHandler<PromotionSelectedEvent> handler) {
    onPromotionSelected.set(handler);
  }

  public final ObjectProperty<EventHandler<ActionEvent>> onCancelProperty() {
    return onCancel;
  }

  public final EventHandler<ActionEvent> getOnCancel() {
    return onCancel.get();
  }

  public final void setOnCancel(EventHandler<ActionEvent> handler) {
    onCancel.set(handler);
  }

  private String displayName(PieceType pieceType) {
    return switch (pieceType) {
      case QUEEN -> "queen";
      case ROOK -> "rook";
      case BISHOP -> "bishop";
      case KNIGHT -> "knight";
      case KING, PAWN -> throw new IllegalArgumentException("Unsupported promotion piece " + pieceType);
    };
  }

  /** JavaFX event emitted after the user chooses a target piece. */
  public static final class PromotionSelectedEvent extends Event {

    public static final EventType<PromotionSelectedEvent> PROMOTION_SELECTED =
        new EventType<>(Event.ANY, "PROMOTION_SELECTED");

    private final PieceType pieceType;

    private PromotionSelectedEvent(PromotionPickerControl source, PieceType pieceType) {
      super(source, source, PROMOTION_SELECTED);
      this.pieceType = Objects.requireNonNull(pieceType, "pieceType must not be null");
    }

    /** Returns the piece type selected by the user. */
    public PieceType pieceType() {
      return pieceType;
    }
  }
}
