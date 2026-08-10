package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;

/**
 * Default skin for {@link ChessBoardControl}.
 *
 * <p>Separates layout grid structures from real-time piece state rendering and input capture
 * events.
 */
public class ChessBoardSkin extends SkinBase<ChessBoardControl> {

  private final GridPane grid = new GridPane();
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

  public ChessBoardSkin(ChessBoardControl control) {

    super(control);
    configureGrid();
    buildGrid(control); // Pasó 1: Construcción estructural del Grid e interacción pura
    loadInitialDemoState(); // Pasó 2: Inyección y renderizado del estado inicial aislado

    // Configurar overlay para pieza flotante
    dragOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    dragOverlay.setMouseTransparent(true);
    draggedPieceView.setPreserveRatio(true);
    draggedPieceView.setOpacity(0.7);
    draggedPieceView.setManaged(false);
    draggedPieceView.setVisible(false);
    dragOverlay.getChildren().add(draggedPieceView);

    // Agregar grid y overlay a la piel
    getChildren().add(grid);
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
        square.setOnMouseClicked(event -> handleSquareClick(control, currentSquare));

        grid.add(square, file, ChessConstants.RANKS - 1 - rank);
      }
    }

    // Manejadores de drag-drop a nivel de grid para permitir arrastres entre casillas
    grid.setOnMousePressed(event -> handleGridMousePressed(control, event));
    grid.setOnMouseDragged(this::handleGridMouseDragged);
    grid.setOnMouseReleased(event -> handleGridMouseReleased(control, event));
  }

  /**
   * RENDERIZADO DE ESTADO INICIAL (Aislado de la estructura): Provee las imágenes iniciales para la
   * maqueta. Puede ser reemplazado en el futuro por un cargador FEN genérico sin alterar la
   * cuadrícula de la UI.
   *
   * <p>TODO: Esto no me gusta demasiado, tendría que ser el Controller principal a través de un
   * método público de ChessBoardControl, quien inicializara el estado del tablero a una posición
   * concreta (FEN) y no la skin. La skin debería ser solo un renderizador de estado.
   */
  private void loadInitialDemoState() {

    for (int rank = 0; rank < ChessConstants.RANKS; rank++) {

      for (int file = 0; file < ChessConstants.FILES; file++) {

        ChessSquareControl square = squares[file][rank];
        String color = rank < 2 ? "white" : rank > 5 ? "black" : null;

        if (color != null) {
          String piece = rank == 1 || rank == 6 ? "pawn" : startingBackRankPiece(file);
          square.setPieceImage("/chess-pieces/" + color + "-" + piece + ".png");

        } else {
          // CORRECCIÓN: Usamos el método de objeto explícito para evitar la ambigüedad del null
          square.setPieceImageObject(null);
        }
      }
    }
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
   * Renders the board from a complete position (FEN). This method is called by the controller after
   * a move has been validated and applied by GameMoveService. It updates all piece images on the
   * board according to the provided position.
   *
   * @param fen The FEN string representing the board position to render.
   */
  public void renderPosition(String fen) {
    // TODO: Parse FEN and update all squares' piece images accordingly.
    // This will be the single entry point for visual board updates after a move is accepted.
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
    dragOverlay.resizeRelocate(x, y, side, side);
  }

  private void applySquareSizes(double squareSize) {

    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {

        squares[file][rank].setSquareSize(squareSize);
      }
    }
  }

  private String startingBackRankPiece(int file) {

    return switch (file) {
      case 0, 7 -> "rook";
      case 1, 6 -> "knight";
      case 2, 5 -> "bishop";
      case 3 -> "queen";
      case 4 -> "king";
      default -> throw new IllegalArgumentException("Invalid chess file: " + file);
    };
  }
}
