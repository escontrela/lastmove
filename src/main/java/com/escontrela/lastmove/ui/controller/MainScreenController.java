package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.GameReplayService;
import com.escontrela.lastmove.infrastructure.support.FileChooserFactory;
import com.escontrela.lastmove.ui.model.MainScreenViewModel;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import org.springframework.stereotype.Component;

/**
 * FXML controller for the main application screen.
 *
 * <p>Delegates all chess logic to application services. This controller is responsible only
 * for routing UI events and updating the view model.
 */
@Component
public class MainScreenController {

    @FXML
    private BorderPane root;

    private final GameLoadService gameLoadService;
    private final GameReplayService gameReplayService;
    private final FileChooserFactory fileChooserFactory;
    private final MainScreenViewModel viewModel;

    public MainScreenController(GameLoadService gameLoadService,
                                GameReplayService gameReplayService,
                                FileChooserFactory fileChooserFactory,
                                MainScreenViewModel viewModel) {
        this.gameLoadService = gameLoadService;
        this.gameReplayService = gameReplayService;
        this.fileChooserFactory = fileChooserFactory;
        this.viewModel = viewModel;
    }

    @FXML
    public void initialize() {
        // TODO: bind view model properties to FXML controls
    }

    @FXML
    public void onOpenPgn() {
        fileChooserFactory.choosePgnFile(root.getScene().getWindow())
                .ifPresent(file -> {
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
}
