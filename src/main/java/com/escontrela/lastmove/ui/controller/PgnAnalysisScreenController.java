package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.service.GameLoadService;
import com.escontrela.lastmove.application.service.GameReplayService;
import com.escontrela.lastmove.ui.support.FileChooserFactory;
import com.escontrela.lastmove.ui.model.MainScreenViewModel;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * FXML controller for the main application screen.
 *
 * <p>Delegates all chess logic to application services. This controller is responsible only
 * for routing UI events and updating the view model.
 */
@Component
public class PgnAnalysisScreenController implements UiScreenController {

    @FXML
    private BorderPane root;

    private final GameLoadService gameLoadService;
    private final GameReplayService gameReplayService;
    private final FileChooserFactory fileChooserFactory;
    private final MainScreenViewModel viewModel;
    private final UiFlowManager uiFlowManager;

    public PgnAnalysisScreenController(GameLoadService gameLoadService,
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

    @FXML
    public void backToMain() {
        uiFlowManager.show(UiScreenId.MAIN);
    }

    @FXML
    public void openSetup() {
        uiFlowManager.show(UiScreenId.SETUP);
    }
}
