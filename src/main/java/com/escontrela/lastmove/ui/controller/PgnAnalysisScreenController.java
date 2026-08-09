package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.GameReplayService;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.model.MainScreenViewModel;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * FXML controller for the main application screen.
 *
 * <p>Delegates all chess logic to application services. This controller is responsible only for
 * routing UI events and updating the view model.
 */
@Component
public class PgnAnalysisScreenController implements UiScreenController {

  private static final String NIGHT_MODE_STYLE_CLASS = "night-mode";
  private static final String LIGHT_LOGO_RESOURCE = "/images/lastmove-chess-logo.png";
  private static final String DARK_LOGO_RESOURCE = "/images/lastmove-chess-logo-dark.png";

  @FXML private StackPane root;
  @FXML private ImageView statusBrandLogo;
  @FXML private ContextualMenuPanel contextualMenuPanel;
  @FXML private com.escontrela.lastmove.ui.component.board.ChessBoardControl chessBoard;

  private final GameLoadService gameLoadService;
  private final GameReplayService gameReplayService;
  private final FileChooserFactory fileChooserFactory;
  private final MainScreenViewModel viewModel;
  private final UiFlowManager uiFlowManager;
  private final ListChangeListener<String> themeStyleListener = change -> updateStatusBrandLogo();

  public PgnAnalysisScreenController(
      GameLoadService gameLoadService,
      GameReplayService gameReplayService,
      FileChooserFactory fileChooserFactory,
      MainScreenViewModel viewModel,
      @Lazy UiFlowManager uiFlowManager) {
    this.gameLoadService = gameLoadService;
    this.gameReplayService = gameReplayService;
    this.fileChooserFactory = fileChooserFactory;
    this.viewModel = viewModel;
    this.uiFlowManager = uiFlowManager;
  }

  @FXML
  public void initialize() {
    root.getProperties().put("controller", this);
    root.getStyleClass().addListener(themeStyleListener);
    updateStatusBrandLogo();
    configureContextMenu();

    // Suscribirse a los eventos de movimiento desde el tablero
    if (chessBoard != null) {
      chessBoard.setOnMoveRequested(
          event -> {
            com.escontrela.lastmove.ui.model.BoardMoveInput moveInput = event.getMoveInput();
            System.out.println("Controlador recibió movimiento: " + moveInput);
            // TODO: Delegar a GameMoveService o ViewModel
          });
    }
  }

  @FXML
  public void onOpenPgn() {
    fileChooserFactory
        .choosePgnFile(root.getScene().getWindow())
        .ifPresent(
            file -> {
              // TODO: call gameLoadService.load(PgnImportRequest.fromFile(file.toPath()))
            });
  }

  @FXML
  public void onNextMove() {
    // TODO: call gameReplayService.next(currentGame)
  }

  @FXML
  public void onPreviousMove() {
    // TODO: call gameReplayService.previous(currentGame)
  }

  @FXML
  public void backToMain() {
    uiFlowManager.show(UiScreenId.MAIN);
  }

  @FXML
  public void openSetup() {
    uiFlowManager.show(UiScreenId.SETUP);
  }

  @FXML
  public void showContextMenu(ContextMenuEvent event) {
    contextualMenuPanel.showAtScene(event.getSceneX(), event.getSceneY());
    event.consume();
  }

  private void configureContextMenu() {
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem("Open PGN…", "⌘ O", event -> onOpenPgn());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Previous move", "←", event -> onPreviousMove());
    contextualMenuPanel.addItem("Next move", "→", event -> onNextMove());
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Back to chess tools", "", event -> backToMain());
    contextualMenuPanel.addItem("Open setup", "", event -> openSetup());
  }

  private void updateStatusBrandLogo() {
    String resource =
        root.getStyleClass().contains(NIGHT_MODE_STYLE_CLASS)
            ? DARK_LOGO_RESOURCE
            : LIGHT_LOGO_RESOURCE;
    statusBrandLogo.setImage(
        new Image(
            Objects.requireNonNull(
                    getClass().getResource(resource),
                    () -> "Missing status logo resource: " + resource)
                .toExternalForm()));
  }
}
