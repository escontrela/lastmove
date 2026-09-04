package com.escontrela.lastmove.ui.component.board;

import com.escontrela.lastmove.domain.common.ChessConstants;
import com.escontrela.lastmove.domain.common.Square;
import com.escontrela.lastmove.domain.game.PositionPiece;
import com.escontrela.lastmove.domain.game.PositionSnapshot;
import com.escontrela.lastmove.ui.model.BoardMoveInput;
import com.escontrela.lastmove.ui.service.ChessSound;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
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

  private static final double COORDINATE_GUTTER_RATIO = 0.035;
  private static final double MIN_COORDINATE_GUTTER = 12.0;
  private static final double MAX_COORDINATE_GUTTER = 22.0;

  private final Region boardInnerGlow = new Region();
  private final GridPane grid = new GridPane();
  private final Region boardFrame = new Region();
  private final Region boardFrameInset = new Region();
  private final Pane arrowOverlay = new Pane();
  private final Pane coordinateOverlay = new Pane();
  private final StackPane dragOverlay = new StackPane(); // Overlay para pieza flotante durante drag
  private final ImageView draggedPieceView = new ImageView(); // Pieza que sigue al cursor
  private final Label[] fileLabels = new Label[ChessConstants.FILES];
  private final Label[] rankLabels = new Label[ChessConstants.RANKS];

  // 1. ESTADO DE RENDERIZADO: Matriz fija para indexar y acceder a los nodos visuales
  private final ChessSquareControl[][] squares =
      new ChessSquareControl[ChessConstants.FILES][ChessConstants.RANKS];

  // Cache de imágenes de piezas para no releer los PNG del classpath en cada renderizado.
  private final Map<String, Image> pieceImages = new HashMap<>();

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
  private final ChangeListener<Boolean> orientationListener =
      (observable, oldValue, flipped) -> applyOrientation();
  private final ChangeListener<Boolean> visualEffectsListener =
      (observable, oldValue, enabled) -> updateSquareVisualEffects(enabled);
  private final ChangeListener<BoardAppearancePreset> appearancePresetListener =
      (observable, oldValue, preset) -> applyAppearancePreset(preset);
  private final ChangeListener<Square> hintSquareListener =
      (observable, oldSquare, newSquare) -> updateHintSquare(oldSquare, newSquare);
  private final ListChangeListener<Square> threatenedSquaresListener = change -> updateThreatenedSquares();

  public ChessBoardSkin(ChessBoardControl control) {

    super(control);
    configureGrid();
    configureCoordinateOverlay();
    configureFrame();
    buildGrid(control); // Pasó 1: Construcción estructural del Grid e interacción pura
    control.positionProperty().addListener(positionListener);
    control.flippedProperty().addListener(orientationListener);
    control.visualEffectsEnabledProperty().addListener(visualEffectsListener);
    control.appearancePresetProperty().addListener(appearancePresetListener);
    control.hintSquareProperty().addListener(hintSquareListener);
    control.observableThreatenedSquares().addListener(threatenedSquaresListener);
    control.observableArrows().addListener(arrowsListener);
    control.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextMenuFilter);
    if (control.getPosition() != null) {
      renderPosition(control.getPosition());
    }
    updateHintSquare(null, control.getHintSquare());
    updateThreatenedSquares();
    applyAppearancePreset(control.getAppearancePreset());

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
    getChildren().add(boardFrame);
    getChildren().add(boardInnerGlow);
    getChildren().add(boardFrameInset);
    getChildren().add(grid);
    getChildren().add(arrowOverlay);
    getChildren().add(dragOverlay);
    getChildren().add(coordinateOverlay);
  }

  private void configureFrame() {
    boardInnerGlow.setMouseTransparent(true);
    boardInnerGlow.getStyleClass().add("board-v2-inner-glow");
    boardFrame.setMouseTransparent(true);
    boardFrame.getStyleClass().add("board-v2-frame");
    boardFrameInset.setMouseTransparent(true);
    boardFrameInset.getStyleClass().add("board-v2-frame-inset");
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

  private void configureCoordinateOverlay() {
    coordinateOverlay.setMouseTransparent(true);
    coordinateOverlay.setSnapToPixel(true);
    coordinateOverlay.getStyleClass().add("board-coordinate-overlay");
    for (int index = 0; index < ChessConstants.FILES; index++) {
      fileLabels[index] = createCoordinateLabel();
      rankLabels[index] = createCoordinateLabel();
      coordinateOverlay.getChildren().addAll(fileLabels[index], rankLabels[index]);
    }
  }

  private Label createCoordinateLabel() {
    Label label = new Label();
    label.setAlignment(Pos.CENTER);
    label.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    label.getStyleClass().add("board-coordinate-label");
    return label;
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
        square.setVisualEffectsEnabled(control.isVisualEffectsEnabled());

        // Almacenamos la referencia en la matriz indexada del estado de renderizado
        squares[file][rank] = square;

        // Captura de Input encapsulando las variables del bucle
        final Square currentSquare = Square.of(file, rank);

        // Soporte para click-click
        square.setOnMouseClicked(
            event -> {
              if (event.getButton() == MouseButton.PRIMARY) {
                if (control.isEditorMode()) {
                  control.handleEditorSquare(currentSquare);
                } else {
                  handleSquareClick(control, currentSquare);
                }
              }
            });
        square.setOnKeyPressed(
            event -> {
              if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)
                  && control.isEditorMode()) {
                control.handleEditorSquare(currentSquare);
                event.consume();
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
    grid.setOnDragOver(event -> handlePaletteDragOver(control, event));
    grid.setOnDragDropped(event -> handlePaletteDrop(control, event));
    applyOrientation();
  }

  private void handlePaletteDragOver(ChessBoardControl control, DragEvent event) {
    if (control.isEditorMode()
        && BoardPieceDragPayload.decode(event.getDragboard().getString()).isPresent()) {
      event.acceptTransferModes(TransferMode.COPY);
    }
    event.consume();
  }

  private void handlePaletteDrop(ChessBoardControl control, DragEvent event) {
    Square target = squareAtCoordinate(event.getX(), event.getY());
    var payload = BoardPieceDragPayload.decode(event.getDragboard().getString());
    boolean completed = control.isEditorMode() && target != null && payload.isPresent();
    if (completed) {
      control.handlePieceDrop(target, payload.orElseThrow());
    }
    event.setDropCompleted(completed);
    event.consume();
  }

  private void updateSquareVisualEffects(boolean enabled) {
    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
        squares[file][rank].setVisualEffectsEnabled(enabled);
      }
    }
    boardInnerGlow.setVisible(getSkinnable().getAppearancePreset().framed() && enabled);
  }

  private void applyAppearancePreset(BoardAppearancePreset preset) {
    pieceImages.clear();
    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
        ChessSquareControl square = squares[file][rank];
        square.setTheme(preset.boardTheme());
        square.setPieceScale(preset.pieceScale());
      }
    }
    boardFrame.setVisible(preset.framed());
    boardFrameInset.setVisible(preset.framed());
    boardInnerGlow.setVisible(preset.framed() && getSkinnable().isVisualEffectsEnabled());
    if (getSkinnable().getPosition() != null) {
      renderPosition(getSkinnable().getPosition());
    }
    getSkinnable().requestLayout();
  }
  private void updateThreatenedSquares() {
    var threatened = new java.util.HashSet<>(getSkinnable().observableThreatenedSquares());
    for (int file=0; file<ChessConstants.FILES; file++) for (int rank=0; rank<ChessConstants.RANKS; rank++) squares[file][rank].setThreatened(threatened.contains(Square.of(file, rank)));
  }

  private void updateHintSquare(Square oldSquare, Square newSquare) {
    if (oldSquare != null) {
      squares[oldSquare.getFile()][oldSquare.getRank()].setHint(false);
    }
    if (newSquare != null) {
      squares[newSquare.getFile()][newSquare.getRank()].setHint(true);
    }
  }

  private void applyOrientation() {
    clickClickPendingSquare = null;
    dragOriginSquare = null;
    draggedPiece = null;
    draggedPieceView.setVisible(false);
    arrowOriginSquare = null;
    previewArrow = null;
    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
        ChessSquareControl square = squares[file][rank];
        Square logicalSquare = Square.of(file, rank);
        GridPane.setColumnIndex(
            square, displayFile(logicalSquare, getSkinnable().isFlipped()));
        GridPane.setRowIndex(
            square, displayRow(logicalSquare, getSkinnable().isFlipped()));
      }
    }
    clearDragTargetHighlight();
    updateCoordinateLabels();
    renderArrows();
  }

  private void updateCoordinateLabels() {
    boolean flipped = getSkinnable().isFlipped();
    for (int index = 0; index < ChessConstants.FILES; index++) {
      fileLabels[index].setText(fileLabelAt(index, flipped));
      rankLabels[index].setText(rankLabelAt(index, flipped));
    }
  }

  private void handleArrowPressed(ChessBoardControl control, MouseEvent event) {
    if (control.isEditorMode()) {
      Square target = squareAtCoordinate(event.getX(), event.getY());
      if (target != null) control.handleEditorSecondarySquare(target);
      event.consume();
      return;
    }
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
    if (getSkinnable().isEditorMode()) { event.consume(); return; }
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
    if (control.isEditorMode()) { event.consume(); return; }
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

    int displayColumn = (int) (gridX / (gridWidth / ChessConstants.FILES));
    int displayRow = (int) (gridY / (gridHeight / ChessConstants.RANKS));

    // Validates that the calculated file and rank are within the valid range of the chessboard
    if (displayColumn < 0
        || displayColumn >= ChessConstants.FILES
        || displayRow < 0
        || displayRow >= ChessConstants.RANKS) {
      return null;
    }

    return logicalSquareAt(displayColumn, displayRow, getSkinnable().isFlipped());
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
        squares[file][rank].clearPieceAccessibility();
      }
    }
    for (PositionPiece piece : snapshot.pieces()) {
      squares[piece.square().getFile()][piece.square().getRank()]
          .setPieceImageObject(pieceImage(piece));
      squares[piece.square().getFile()][piece.square().getRank()]
          .setPieceAccessibility(piece.type(), piece.color());
    }
  }

  void showFeedback(java.util.Set<Square> correct, java.util.Set<Square> incorrect) {
    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
        Square square = Square.of(file, rank);
        squares[file][rank].setAnswerFeedback(
            correct.contains(square) ? Boolean.TRUE : incorrect.contains(square) ? Boolean.FALSE : null);
      }
    }
  }

  void clearFeedback() {
    for (int file = 0; file < ChessConstants.FILES; file++) for (int rank = 0; rank < ChessConstants.RANKS; rank++) {
      squares[file][rank].setAnswerFeedback(null);
    }
  }

  private Image pieceImage(PositionPiece piece) {
    String key = piece.color().name().toLowerCase() + "-" + piece.type().name().toLowerCase();
    String root = getSkinnable().getAppearancePreset().pieceResourceRoot();
    return pieceImages.computeIfAbsent(root + "/" + key, unused -> loadPieceImage(root, key));
  }

  private Image loadPieceImage(String root, String key) {
    String resourcePath = root + "/" + key + ".png";
    var stream = ChessBoardSkin.class.getResourceAsStream(resourcePath);
    if (stream == null) {
      throw new IllegalArgumentException("No se encontró el recurso: " + resourcePath);
    }
    return new Image(stream);
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
    boolean flipped = getSkinnable().isFlipped();
    double startX = (displayFile(arrow.from(), flipped) + 0.5) * squareSize;
    double startY = (displayRow(arrow.from(), flipped) + 0.5) * squareSize;
    double targetX = (displayFile(arrow.to(), flipped) + 0.5) * squareSize;
    double targetY = (displayRow(arrow.to(), flipped) + 0.5) * squareSize;
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
    getSkinnable().flippedProperty().removeListener(orientationListener);
    getSkinnable().visualEffectsEnabledProperty().removeListener(visualEffectsListener);
    getSkinnable().appearancePresetProperty().removeListener(appearancePresetListener);
    getSkinnable().hintSquareProperty().removeListener(hintSquareListener);
    getSkinnable().observableThreatenedSquares().removeListener(threatenedSquaresListener);
    getSkinnable().observableArrows().removeListener(arrowsListener);
    getSkinnable().removeEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, contextMenuFilter);
    super.dispose();
  }

  @Override
  protected void layoutChildren(
      double contentX, double contentY, double contentWidth, double contentHeight) {

    double availableSide = Math.floor(Math.min(contentWidth, contentHeight));
    if (getSkinnable().getAppearancePreset().framed()) {
      layoutV2Board(contentX, contentY, contentWidth, contentHeight, availableSide);
      return;
    }

    // The standard board keeps its coordinate gutter inside the assigned square.
    double gutter = coordinateGutter(availableSide);
    double boardSide = boardSideFor(availableSide, gutter);
    double usedSide = boardSide + gutter;
    double x = Math.floor(contentX + (contentWidth - usedSide) / 2.0);
    double y = Math.floor(contentY + (contentHeight - usedSide) / 2.0);

    // Calculamos nosotros mismos el tamaño exacto de cada casilla y lo aplicamos
    // explícitamente, en lugar de dejar que JavaFX (CSS/GridPane) decida el redimensionado.
    // Esto evita que las casillas se queden ancladas a un tamaño mínimo fijo cuando la ventana
    // no está maximizada.
    double squareSize = boardSide / ChessConstants.FILES;
    applySquareSizes(squareSize);

    boardInnerGlow.resizeRelocate(x, y, 0, 0);
    boardFrame.resizeRelocate(x, y, 0, 0);
    boardFrameInset.resizeRelocate(x, y, 0, 0);
    grid.resizeRelocate(x, y, boardSide, boardSide);
    arrowOverlay.resizeRelocate(x, y, boardSide, boardSide);
    dragOverlay.resizeRelocate(x, y, boardSide, boardSide);
    coordinateOverlay.resizeRelocate(x, y, usedSide, usedSide);
    layoutCoordinateLabels(boardSide, gutter, squareSize);
    renderArrows();
  }

  private void layoutV2Board(
      double contentX,
      double contentY,
      double contentWidth,
      double contentHeight,
      double availableSide) {
    double frameThickness = v2FrameThickness(availableSide);
    double boardSide = v2BoardSideFor(availableSide, frameThickness);
    double frameSide = boardSide + frameThickness * 2.0;
    double frameX = Math.floor(contentX + (contentWidth - frameSide) / 2.0);
    double frameY = Math.floor(contentY + (contentHeight - frameSide) / 2.0);
    double boardX = frameX + frameThickness;
    double boardY = frameY + frameThickness;
    double squareSize = boardSide / ChessConstants.FILES;
    applySquareSizes(squareSize);

    boardFrame.resizeRelocate(frameX, frameY, frameSide, frameSide);
    double innerLip = Math.max(3.0, Math.floor(frameThickness * 0.18));
    double glowLip = innerLip + 1.0;
    boardInnerGlow.resizeRelocate(
        boardX - glowLip,
        boardY - glowLip,
        boardSide + glowLip * 2.0,
        boardSide + glowLip * 2.0);
    boardFrameInset.resizeRelocate(
        boardX - innerLip,
        boardY - innerLip,
        boardSide + innerLip * 2.0,
        boardSide + innerLip * 2.0);
    grid.resizeRelocate(boardX, boardY, boardSide, boardSide);
    arrowOverlay.resizeRelocate(boardX, boardY, boardSide, boardSide);
    dragOverlay.resizeRelocate(boardX, boardY, boardSide, boardSide);
    coordinateOverlay.resizeRelocate(frameX, frameY, frameSide, frameSide);
    layoutV2CoordinateLabels(frameThickness, boardSide, squareSize);
    renderArrows();
  }

  private void layoutV2CoordinateLabels(
      double frameThickness, double boardSide, double squareSize) {
    double labelExtent = Math.min(18.0, Math.max(12.0, Math.floor(frameThickness * 0.55)));
    double axisInset = Math.floor((frameThickness - labelExtent) / 2.0);
    for (int index = 0; index < ChessConstants.FILES; index++) {
      fileLabels[index].resizeRelocate(
          frameThickness + index * squareSize,
          frameThickness + boardSide + axisInset,
          squareSize,
          labelExtent);
      rankLabels[index].resizeRelocate(
          axisInset,
          frameThickness + index * squareSize + Math.floor((squareSize - labelExtent) / 2.0),
          labelExtent,
          labelExtent);
    }
  }

  private void layoutCoordinateLabels(double boardSide, double gutter, double squareSize) {
    // The clear strip sits inside the existing coordinate gutter, so this visual adjustment never
    // changes the board side or the exact size of its eight-by-eight square grid.
    double labelGap = Math.min(5.0, Math.max(0.0, gutter - 1.0));
    double labelExtent = Math.max(1.0, gutter - labelGap);
    for (int index = 0; index < ChessConstants.FILES; index++) {
      fileLabels[index].resizeRelocate(index * squareSize, boardSide + labelGap, squareSize, labelExtent);
      rankLabels[index].resizeRelocate(boardSide + labelGap, index * squareSize, labelExtent, squareSize);
    }
  }

  private void applySquareSizes(double squareSize) {

    for (int file = 0; file < ChessConstants.FILES; file++) {
      for (int rank = 0; rank < ChessConstants.RANKS; rank++) {

        squares[file][rank].setSquareSize(squareSize);
      }
    }
  }

  static int displayFile(Square square, boolean flipped) {
    return flipped ? ChessConstants.FILES - 1 - square.getFile() : square.getFile();
  }

  static int displayRow(Square square, boolean flipped) {
    return flipped ? square.getRank() : ChessConstants.RANKS - 1 - square.getRank();
  }

  static Square logicalSquareAt(int displayColumn, int displayRow, boolean flipped) {
    int file = flipped ? ChessConstants.FILES - 1 - displayColumn : displayColumn;
    int rank = flipped ? displayRow : ChessConstants.RANKS - 1 - displayRow;
    return Square.of(file, rank);
  }

  static String fileLabelAt(int displayColumn, boolean flipped) {
    int file = flipped ? ChessConstants.FILES - 1 - displayColumn : displayColumn;
    return String.valueOf((char) ('a' + file));
  }

  static String rankLabelAt(int displayRow, boolean flipped) {
    int rank = flipped ? displayRow : ChessConstants.RANKS - 1 - displayRow;
    return Integer.toString(rank + 1);
  }

  static double v2FrameThickness(double availableSide) {
    if (availableSide <= 0.0) {
      return 0.0;
    }
    double preferred = Math.min(36.0, Math.max(16.0, Math.floor(availableSide * 0.05)));
    return Math.min(preferred, availableSide / 2.0);
  }

  static double v2BoardSideFor(double availableSide, double frameThickness) {
    double drawableSide = Math.max(0.0, availableSide - frameThickness * 2.0);
    return Math.floor(drawableSide / ChessConstants.FILES) * ChessConstants.FILES;
  }

  static double coordinateGutter(double availableSide) {
    if (availableSide <= 0.0) {
      return 0.0;
    }
    double preferred =
        Math.max(
            MIN_COORDINATE_GUTTER,
            Math.min(MAX_COORDINATE_GUTTER, Math.floor(availableSide * COORDINATE_GUTTER_RATIO)));
    return Math.min(availableSide, preferred);
  }

  static double boardSideFor(double availableSide, double gutter) {
    double rawBoardSide = Math.max(0.0, availableSide - gutter);
    double quantized =
        Math.floor(rawBoardSide / ChessConstants.FILES) * ChessConstants.FILES;
    return quantized > 0.0 ? quantized : rawBoardSide;
  }

}
