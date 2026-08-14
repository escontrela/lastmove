package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.service.ChessSound;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;

/**
 * Default skin for {@link ChessBoardControl}.
 *
 * <p>Separates layout grid structures from real-time piece state rendering and input capture
 * events.
 */
public class ChessBoardSkin extends SkinBase<ChessBoardControl> {

  private final GridPane grid = new GridPane();
  private final Pane arrowOverlay = new Pane();
  private final StackPane dragOverlay = new StackPane(); // Overlay para pieza flotante durante drag
  private final ImageView draggedPieceView = new ImageView(); // Pieza que sigue al cursor

  // 1. ESTADO DE RENDERIZADO: Matriz fija para indexar y acceder a los nodos visuales
  private final ChessSquareControl[][] squares =
      new ChessSquareControl[ChessConstants.FILES][ChessConstants.RANKS];

  // 2. ESTADO DE CAPTURA DE INPUT: Separado por tipo de interacción para evitar interferencias
  // Click-click: almacena la primera casilla hasta que se hace clic en la segunda
  private Square clickClickPendingSquare = null;

  // Drag-drop: almacena el origen del arrastre
  private Square dragOriginSquare = null;
  private Image draggedPiece = null; // La imagen de la pieza durante el drag
  private Square hoveredDragSquare = null; // Casilla actual bajo el cursor durante drag
  private Square arrowOriginSquare = null;
  private BoardArrow previewArrow = null;
  private final ListChangeListener<BoardArrow> arrowsListener = change -> renderArrows();
  private final EventHandler<ContextMenuEvent> contextMenuFilter = event -> event.consume();
  private final ChangeListener<PositionSnapshot> positionListener =
      (observable, oldPosition, newPosition) -> {
        if (oldPosition != null && !oldPosition.equals(newPosition)) {
          getSkinnable().clearArrows();
        }
        renderPosition(newPosition);
        playMoveSoundIfNeeded(oldPosition, newPosition);
      };

  public ChessBoardSkin(ChessBoardControl control) {

    super(control);
    configureGrid();
    buildGrid(control); // Pasó 1: Construcción estructural del Grid e interacción pura
    control.positionProperty().addListener(positionListener);
    control.observableArrows().addListener(arrowsListener);
    control.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextMenuFilter);
    if (control.getPosition() != null) {
      renderPosition(control.getPosition());
    }

    // Configurar overlay para pieza flotante
    dragOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    dragOverlay.setMouseTransparent(true);
    draggedPieceView.setPreserveRatio(true);
    draggedPieceView.setOpacity(0.7);
    draggedPieceView.setManaged(false);
    draggedPieceView.setVisible(false);
    dragOverlay.getChildren().add(draggedPieceView);

    arrowOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    arrowOverlay.setMouseTransparent(true);
    arrowOverlay.setSnapToPixel(true);

    // Arrows sit above squares and pieces, while the drag feedback remains the topmost overlay.
    getChildren().add(grid);
    getChildren().add(arrowOverlay);
    getChildren().add(dragOverlay);
  }

  private void configureGrid() {

    grid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    grid.setSnapToPixel(true);
    dragOverlay.setSnapToPixel(true);

    for (int index = 0; index < ChessConstants.FILES; index++) {

      ColumnConstraints column = new ColumnConstraints();
      column.setPercentWidth(100.0 / ChessConstants.FILES);
      column.setFillWidth(true);
      grid.getColumnConstraints().add(column);
    }

    for (int index = 0; index < ChessConstants.RANKS; index++) {

      RowConstraints row = new RowConstraints();
      row.setPercentHeight(100.0 / ChessConstants.RANKS);
      row.setFillHeight(true);
      grid.getRowConstraints().add(row);
    }
  }

  /**
   * CONSTRUCCIÓN ESTRUCTURAL Y CAPTURA DE INPUT: Instancia las casillas visuales en blanco, mapea
   * las coordenadas y asienta los Listeners para click-click y drag-drop.
   */
  private void buildGrid(ChessBoardControl control) {

    for (int rank = ChessConstants.RANKS - 1; rank >= 0; rank--) {

      for (int file = 0; file < ChessConstants.FILES; file++) {

        boolean isLight = (file + rank) % 2 != 0;
        ChessSquareControl square = new ChessSquareControl(file, rank, isLight, control.getTheme());

        // Almacenamos la referencia en la matriz indexada del estado de renderizado
        squares[file][rank] = square;

        // Captura de Input encapsulando las variables del bucle
        final Square currentSquare = Square.of(file, rank);

        // Soporte para click-click
        square.setOnMouseClicked(
            event -> {
              if (event.getButton() == MouseButton.PRIMARY) {
                handleSquareClick(control, currentSquare);
              }
            });

        grid.add(square, file, ChessConstants.RANKS - 1 - rank);
      }
    }

    // Manejadores de drag-drop a nivel de grid para permitir arrastres entre casillas
    grid.setOnMousePressed(
        event -> {
          if (event.getButton() == MouseButton.SECONDARY) {
            handleArrowPressed(control, event);
          } else if (event.getButton() == MouseButton.PRIMARY) {
            handleGridMousePressed(control, event);
          }
        });
    grid.setOnMouseDragged(
        event -> {
          if (event.isSecondaryButtonDown()) {
            handleArrowDragged(event);
          } else if (event.isPrimaryButtonDown()) {
            handleGridMouseDragged(event);
          }
        });
    grid.setOnMouseReleased(
        event -> {
          if (event.getButton() == MouseButton.SECONDARY) {
            handleArrowReleased(control, event);
          } else if (event.getButton() == MouseButton.PRIMARY) {
            handleGridMouseReleased(control, event);
          }
        });
    grid.setOnMouseClicked(
        event -> {
          if (event.getButton() == MouseButton.SECONDARY && event.getClickCount() >= 2) {
            arrowOriginSquare = null;
            previewArrow = null;
            control.clearArrows();
            renderArrows();
            event.consume();
          }
        });
  }

  private void handleArrowPressed(ChessBoardControl control, MouseEvent event) {
    if (event.getClickCount() >= 2) {
      arrowOriginSquare = null;
      previewArrow = null;
      control.clearArrows();
      renderArrows();
      event.consume();
      return;
    }
    arrowOriginSquare = squareAtCoordinate(event.getX(), event.getY());
    previewArrow = null;
    event.consume();
  }

  private void handleArrowDragged(MouseEvent event) {
    if (arrowOriginSquare == null) {
      return;
    }
    Square target = squareAtCoordinate(event.getX(), event.getY());
    previewArrow =
        target == null || target.equals(arrowOriginSquare)
            ? null
            : new BoardArrow(arrowOriginSquare, target);
    renderArrows();
    event.consume();
  }

  private void handleArrowReleased(ChessBoardControl control, MouseEvent event) {
    if (arrowOriginSquare == null) {
      event.consume();
      return;
    }
    Square target = squareAtCoordinate(event.getX(), event.getY());
    if (target != null && !target.equals(arrowOriginSquare)) {
      control.toggleArrow(new BoardArrow(arrowOriginSquare, target));
    }
    arrowOriginSquare = null;
    previewArrow = null;
    renderArrows();
    event.consume();
  }

  /**
   * Input flow: Handles click-click interaction on the chessboard. On the first click, it registers
   * the selected square. On the second click, it attempts to move from the selected square to the
   * clicked square.
   *
   * @param control The ChessBoardControl instance that owns this skin.
   * @param clickedSquare The square that was clicked by the user.
   */
  private void handleSquareClick(ChessBoardControl control, Square clickedSquare) {

    if (clickClickPendingSquare == null) {

      // First click: Register the selected square for click-click mode
      clickClickPendingSquare = clickedSquare;

    } else {

      // Second click: Attempt to move from the first click to this one
      Square from = clickClickPendingSquare;
      Square to = clickedSquare;
      // Reset immediately for future interactions
      clickClickPendingSquare = null;

      if (from.equals(to)) {
        return; // Cancel if same square
      }

      // Emit the move input to the native control
      control.handleBoardMoveInput(BoardMoveInput.from(from, to));
    }
  }

  /**
   * DRAG-DROP INTERACTION FLOW: Captures mouse pressed events on the grid, determines the square
   * under the cursor, and initiates a drag operation if a piece is present. It also provides visual
   * feedback by displaying the dragged piece and highlighting potential target squares.
   *
   * @param control The ChessBoardControl instance that owns this skin.
   * @param event The MouseEvent triggered by the user's interaction.
   */
  private void handleGridMousePressed(ChessBoardControl control, MouseEvent event) {

    Square pressedSquare = squareAtCoordinate(event.getX(), event.getY());

    if (pressedSquare != null) {

      dragOriginSquare = pressedSquare;

      // Capturar la imagen de la pieza para el feedback visual
      draggedPiece = squares[pressedSquare.getFile()][pressedSquare.getRank()].getPieceImage();
      if (draggedPiece != null) {

        draggedPieceView.setImage(draggedPiece);
        draggedPieceView.setVisible(true);
        draggedPieceView.setFitWidth(grid.getWidth() / ChessConstants.FILES * 0.82);
        draggedPieceView.setFitHeight(grid.getHeight() / ChessConstants.RANKS * 0.82);

        updateDraggedPiecePosition(event.getX(), event.getY());
        updateDragTargetHighlight(pressedSquare);
      }
    }
  }

  /**
   * VISUAL FEEDBACK DURING DRAG-DROP: Updates the position of the dragged piece image and
   * highlights the square under the cursor. This method is called continuously as the user drags
   * the mouse across the chessboard.
   *
   * @param event The MouseEvent triggered by the user's drag action.
   */
  private void handleGridMouseDragged(MouseEvent event) {

    if (draggedPiece != null && draggedPieceView.isVisible()) {

      updateDraggedPiecePosition(event.getX(), event.getY());
      updateDragTargetHighlight(squareAtCoordinate(event.getX(), event.getY()));
    }
  }

  /**
   * Converts grid coordinates to a Square object. Returns null if the coordinates are outside the
   * board.
   *
   * @param gridX The x-coordinate relative to the grid.
   * @param gridY The y-coordinate relative to the grid.
   * @return The Square corresponding to the grid coordinates, or null if outside the board.
   */
  private Square squareAtCoordinate(double gridX, double gridY) {

    double gridWidth = grid.getWidth();
    double gridHeight = grid.getHeight();

    if (gridX < 0 || gridX > gridWidth || gridY < 0 || gridY > gridHeight) {

      return null;
    }

    int file = (int) (gridX / (gridWidth / ChessConstants.FILES));
    int rank = ChessConstants.RANKS - 1 - (int) (gridY / (gridHeight / ChessConstants.RANKS));

    // Validates that the calculated file and rank are within the valid range of the chessboard
    if (file < 0 || file >= ChessConstants.FILES || rank < 0 || rank >= ChessConstants.RANKS) {
      return null;
    }

    return Square.of(file, rank);
  }

  private void updateDragTargetHighlight(Square candidateSquare) {

    if (hoveredDragSquare != null && !hoveredDragSquare.equals(candidateSquare)) {

      squares[hoveredDragSquare.getFile()][hoveredDragSquare.getRank()].setDragTarget(false);
      hoveredDragSquare = null;
    }

    if (candidateSquare != null && !candidateSquare.equals(hoveredDragSquare)) {

      squares[candidateSquare.getFile()][candidateSquare.getRank()].setDragTarget(true);
      hoveredDragSquare = candidateSquare;
    }
  }

  private void clearDragTargetHighlight() {

    if (hoveredDragSquare != null) {

      squares[hoveredDragSquare.getFile()][hoveredDragSquare.getRank()].setDragTarget(false);
      hoveredDragSquare = null;
    }
  }

  private void updateDraggedPiecePosition(double x, double y) {

    double offsetX = draggedPieceView.getFitWidth() / 2;
    double offsetY = draggedPieceView.getFitHeight() / 2;

    draggedPieceView.setLayoutX(x - offsetX);
    draggedPieceView.setLayoutY(y - offsetY);
  }

  /**
   * DRAG-DROP INTERACTION FLOW: Captures mouse released events on the grid, determines the square
   * under the cursor, and finalizes the drag operation. Emits a BoardMoveInput event for the
   * controller/service layer to validate and apply.
   *
   * @param control The ChessBoardControl instance that owns this skin.
   * @param event The MouseEvent triggered by the user's release action.
   */
  private void handleGridMouseReleased(ChessBoardControl control, MouseEvent event) {

    Square releasedSquare = squareAtCoordinate(event.getX(), event.getY());

    // Clean up floating piece visual
    draggedPieceView.setVisible(false);
    draggedPiece = null;
    clearDragTargetHighlight();

    if (dragOriginSquare == null) {
      return; // No active drag operation
    }

    Square from = dragOriginSquare;
    Square to = releasedSquare;

    // Reset the drag origin for future interactions
    dragOriginSquare = null;

    // The release square might be null if the mouse was released outside the board
    if (to == null) {
      return;
    }

    // If the user released on the same square, we do nothing
    if (from.equals(to)) {
      return;
    }

    // Emit the move input to the native control (validation happens in application layer)
    control.handleBoardMoveInput(BoardMoveInput.from(from, to));
  }

  /**
   * Renders the board from an engine-independent position snapshot. This is called by the
   * controller after a move has been applied by the analysis-session use case.
   *
   * @param snapshot the complete board state to render.
   */
  private void renderPosition(PositionSnapshot snapshot) {
    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
        squares[file][rank].setPieceImageObject(null);
      }
    }
    for (PositionPiece piece : snapshot.pieces()) {
      squares[piece.square().getFile()][piece.square().getRank()]
          .setPieceImage(pieceResource(piece));
    }
  }

  private String pieceResource(PositionPiece piece) {
    String color = piece.color().name().toLowerCase();
    String type = piece.type().name().toLowerCase();
    return "/chess-pieces/" + color + "-" + type + ".png";
  }

  private void playMoveSoundIfNeeded(PositionSnapshot previous, PositionSnapshot current) {
    if (previous == null
        || current.lastMove().isEmpty()
        || current.lastMove().equals(previous.lastMove())) {
      return;
    }
    var move = current.lastMove().orElseThrow();
    ChessSound sound =
        current.check()
            ? ChessSound.MOVE_CHECK
            : move.promotion().isPresent()
                ? ChessSound.PROMOTE
                : move.castling()
                    ? ChessSound.CASTLE
                    : move.capture() ? ChessSound.CAPTURE : ChessSound.MOVE_SELF;
    getSkinnable().playSound(sound);
  }

  private void renderArrows() {
    arrowOverlay.getChildren().clear();
    if (arrowOverlay.getWidth() <= 0 || arrowOverlay.getHeight() <= 0) {
      return;
    }
    for (BoardArrow arrow : getSkinnable().getArrows()) {
      arrowOverlay.getChildren().add(createArrowGraphic(arrow, 0.74));
    }
    if (previewArrow != null) {
      arrowOverlay.getChildren().add(createArrowGraphic(previewArrow, 0.48));
    }
  }

  private Group createArrowGraphic(BoardArrow arrow, double opacity) {
    double squareSize = arrowOverlay.getWidth() / ChessConstants.FILES;
    double startX = (arrow.from().getFile() + 0.5) * squareSize;
    double startY = (ChessConstants.RANKS - arrow.from().getRank() - 0.5) * squareSize;
    double targetX = (arrow.to().getFile() + 0.5) * squareSize;
    double targetY = (ChessConstants.RANKS - arrow.to().getRank() - 0.5) * squareSize;
    double deltaX = targetX - startX;
    double deltaY = targetY - startY;
    double distance = Math.hypot(deltaX, deltaY);
    double unitX = deltaX / distance;
    double unitY = deltaY / distance;
    double headLength = squareSize * 0.34;
    double headHalfWidth = squareSize * 0.22;
    double baseX = targetX - unitX * headLength;
    double baseY = targetY - unitY * headLength;

    Color color = Color.web("#4f9b3d", opacity);
    Line shaft = new Line(startX, startY, baseX + unitX * squareSize * 0.08, baseY + unitY * squareSize * 0.08);
    shaft.setStroke(color);
    shaft.setStrokeWidth(squareSize * 0.13);
    shaft.setStrokeLineCap(StrokeLineCap.ROUND);

    double perpendicularX = -unitY;
    double perpendicularY = unitX;
    Polygon head = new Polygon();
    head.getPoints()
        .addAll(
            targetX,
            targetY,
            baseX + perpendicularX * headHalfWidth,
            baseY + perpendicularY * headHalfWidth,
            baseX - perpendicularX * headHalfWidth,
            baseY - perpendicularY * headHalfWidth);
    head.setFill(color);
    return new Group(shaft, head);
  }

  @Override
  public void dispose() {
    getSkinnable().positionProperty().removeListener(positionListener);
    getSkinnable().observableArrows().removeListener(arrowsListener);
    getSkinnable().removeEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextMenuFilter);
    super.dispose();
  }

  @Override
  protected void layoutChildren(
      double contentX, double contentY, double contentWidth, double contentHeight) {

    // Cuantizamos el lado a múltiplos de 8 para que cada casilla tenga tamaño entero.
    double rawSide = Math.floor(Math.min(contentWidth, contentHeight));
    double quantizedSide = Math.floor(rawSide / ChessConstants.FILES) * ChessConstants.FILES;
    double side = quantizedSide > 0 ? quantizedSide : rawSide;
    double x = Math.floor(contentX + (contentWidth - side) / 2.0);
    double y = Math.floor(contentY + (contentHeight - side) / 2.0);

    // Calculamos nosotros mismos el tamaño exacto de cada casilla y lo aplicamos
    // explícitamente, en lugar de dejar que JavaFX (CSS/GridPane) decida el redimensionado.
    // Esto evita que las casillas se queden ancladas a un tamaño mínimo fijo cuando la ventana
    // no está maximizada.
    applySquareSizes(side / ChessConstants.FILES);

    grid.resizeRelocate(x, y, side, side);
    arrowOverlay.resizeRelocate(x, y, side, side);
    dragOverlay.resizeRelocate(x, y, side, side);
    renderArrows();
  }

  private void applySquareSizes(double squareSize) {

    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {

        squares[file][rank].setSquareSize(squareSize);
      }
    }
  }

}
