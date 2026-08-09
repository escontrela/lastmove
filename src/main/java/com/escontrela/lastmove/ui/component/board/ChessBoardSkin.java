package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import java.util.Optional;
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

  // 2. ESTADO DE CAPTURA DE INPUT: Almacena de forma volátil el origen de la interacción
  private Square selectedSquare = null;
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
   * FLUJO DE INTERACCIÓN DE ENTRADA (Manejador de clics): Conduce la máquina de estados lógicos
   * Origen -> Destino y emite hacia el Control.
   */
  private void handleSquareClick(ChessBoardControl control, Square clickedSquare) {
    if (selectedSquare == null) {
      // Primer clic: Fijamos origen
      selectedSquare = clickedSquare;
    } else {
      // Segundo clic: Evaluamos destino
      Square from = selectedSquare;
      Square to = clickedSquare;
      selectedSquare = null; // Reinicio inmediato del estado lógico de captura

      if (from.equals(to)) {
        return; // Cancelación si pulsa la misma casilla
      }

      // Despachamos el objeto de datos hacia el control nativo
      BoardMoveInput moveInput = new BoardMoveInput(from, to, Optional.empty());
      control.handleBoardMoveInput(moveInput);
    }
  }

  /**
   * Convierte coordenadas de mouse relativas al grid en una Square. Retorna null si están fuera del
   * tablero.
   */
  private Square squareAtCoordinate(double gridX, double gridY) {
    double gridWidth = grid.getWidth();
    double gridHeight = grid.getHeight();

    if (gridX < 0 || gridX > gridWidth || gridY < 0 || gridY > gridHeight) {
      return null;
    }

    int file = (int) (gridX / (gridWidth / ChessConstants.FILES));
    int rank = ChessConstants.RANKS - 1 - (int) (gridY / (gridHeight / ChessConstants.RANKS));

    // Validar que el archivo y rango estén dentro del tablero
    if (file < 0 || file >= ChessConstants.FILES || rank < 0 || rank >= ChessConstants.RANKS) {
      return null;
    }

    return Square.of(file, rank);
  }

  /** FLUJO DE INTERACCIÓN DRAG-DROP: Registro del mouse pressed a nivel de grid. */
  private void handleGridMousePressed(ChessBoardControl control, MouseEvent event) {
    Square pressedSquare = squareAtCoordinate(event.getX(), event.getY());
    if (pressedSquare != null) {
      System.out.println("[DEBUG] Mouse pressed en: " + pressedSquare);
      selectedSquare = pressedSquare;

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

      System.out.println("[DEBUG] Origen de drag registrado: " + selectedSquare);
    }
  }

  /** Feedback visual: Sigue la pieza flotante con el cursor durante el drag. */
  private void handleGridMouseDragged(MouseEvent event) {
    if (draggedPiece != null && draggedPieceView.isVisible()) {
      updateDraggedPiecePosition(event.getX(), event.getY());
      updateDragTargetHighlight(squareAtCoordinate(event.getX(), event.getY()));
    }
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

  /** FLUJO DE INTERACCIÓN DRAG-DROP: Registro del mouse released a nivel de grid. */
  private void handleGridMouseReleased(ChessBoardControl control, MouseEvent event) {
    Square releasedSquare = squareAtCoordinate(event.getX(), event.getY());
    System.out.println(
        "[DEBUG] Mouse released en: " + releasedSquare + " | selectedSquare: " + selectedSquare);

    // Limpiar la pieza flotante
    draggedPieceView.setVisible(false);
    draggedPiece = null;
    clearDragTargetHighlight();

    if (selectedSquare == null) {
      return; // No hay drag pendiente
    }

    Square from = selectedSquare;
    Square to = releasedSquare;
    selectedSquare = null; // Reset del estado de captura

    // Si release está fuera del tablero, cancelar
    if (to == null) {
      System.out.println("[DEBUG] Movimiento cancelado (release fuera del tablero)");
      return;
    }

    if (from.equals(to)) {
      System.out.println("[DEBUG] Movimiento cancelado (origen == destino)");
      return; // Cancelación si suelta en la misma casilla
    }

    // Validar el movimiento (TODO: conectar con GameMoveService)
    if (validateMove(from, to)) {
      System.out.println(
          "[DEBUG] Movimiento válido, aplicando cambio visual: " + from + " -> " + to);
      // Actualizar la posición visual de la pieza
      refreshMove(from, to);
    } else {
      System.out.println("[DEBUG] Movimiento rechazado (no es legal)");
    }

    // Despachamos el objeto de datos hacia el control nativo
    System.out.println("[DEBUG] Emitiendo BoardMoveInput: " + from + " -> " + to);
    BoardMoveInput moveInput = new BoardMoveInput(from, to, Optional.empty());
    control.handleBoardMoveInput(moveInput);
  }

  /**
   * Valida si un movimiento es legal. TODO: Integrar con GameMoveService + Chesspresso para
   * validación real. Por ahora, acepta todos los movimientos.
   */
  private boolean validateMove(Square from, Square to) {
    // TODO: Conectar con GameMoveService y validación a través de Chesspresso
    return true; // Acepta todos los movimientos por ahora
  }

  /**
   * ACTUALIZACIÓN DINÁMICA DE PIEZAS (Lógica de refresco en tiempo real): Desplaza visualmente la
   * pieza leyendo del índice de componentes.
   */
  public void refreshMove(Square from, Square to) {
    ChessSquareControl fromVisual = squares[from.getFile()][from.getRank()];
    ChessSquareControl toVisual = squares[to.getFile()][to.getRank()];

    // Extraemos la instancia Image de JavaFX de la casilla origen
    javafx.scene.image.Image movingPiece = fromVisual.getPieceImage();

    // La inyectamos en el destino y limpiamos el origen de forma segura
    toVisual.setPieceImage(movingPiece);
    fromVisual.setPieceImage((javafx.scene.image.Image) null);
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
