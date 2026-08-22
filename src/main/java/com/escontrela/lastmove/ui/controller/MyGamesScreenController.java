package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.ResumeComputerGameEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.list.ManagedListCell;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** History of games owned by the current player. */
@Component
public final class MyGamesScreenController implements UiScreenController {
  private final SavedGameRepository games; private final CurrentUserService currentUser;
  private final AnalysisSessionService analyses; private final UiEventBus events; private final UiFlowManager flow;
  @FXML private StackPane root; @FXML private ListView<SavedGameSummary> gamesList; @FXML private Label emptyLabel; @FXML private Label gameCountLabel; @FXML private Label statusLabel; @FXML private ContextualMenuPanel contextualMenuPanel;
  public MyGamesScreenController(SavedGameRepository games, CurrentUserService currentUser, AnalysisSessionService analyses, UiEventBus events, @Lazy UiFlowManager flow) {
    this.games=games; this.currentUser=currentUser; this.analyses=analyses; this.events=events; this.flow=flow;
  }
  @FXML public void initialize() { root.getProperties().put("controller",this); gamesList.setCellFactory(v -> new Cell()); }
  @Override public void onShow() { refresh(); }
  @FXML public void backToHome() { flow.show(UiScreenId.MAIN); }
  private void refresh() { List<SavedGameSummary> rows=currentUser.selectedPlayerId().map(games::listSummaries).orElse(List.of()); gamesList.getItems().setAll(rows); gameCountLabel.setText(rows.size() + (rows.size() == 1 ? " game" : " games")); emptyLabel.setVisible(rows.isEmpty()); emptyLabel.setManaged(rows.isEmpty()); statusLabel.setText(rows.isEmpty() ? "Ready to start your first game" : "Open a game to resume or review it"); }
  private void open(SavedGameSummary game) {
    if (!game.finished()) { flow.show(UiScreenId.HUMAN_VS_COMPUTER); events.publish(new ResumeComputerGameEvent(game.gameId())); return; }
    var session=analyses.createFromGame(games.findSaved(game.gameId()).orElseThrow().game().toRecord());
    events.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Opened saved game")); flow.show(UiScreenId.PGN_ANALYSIS);
  }
  private void showActions(SavedGameSummary game, double x, double y) {
    contextualMenuPanel.clearItems();
    contextualMenuPanel.addItem(game.finished() ? "Review game" : "Resume game", "", e -> open(game));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete game…", "", e -> { games.deleteById(game.gameId()); refresh(); statusLabel.setText("Deleted game: " + game.whiteName() + " vs " + game.blackName()); });
    contextualMenuPanel.showAtScene(x, y);
  }
  private final class Cell extends ManagedListCell<SavedGameSummary> {
    private final HBox row = new HBox(12); private final VBox details = new VBox(4); private final Label title = new Label(); private final Label summary = new Label();
    private Cell() { getStyleClass().add("study-library-cell"); row.getStyleClass().add("study-library-row"); row.setAlignment(Pos.CENTER_LEFT); title.getStyleClass().add("study-library-title"); summary.getStyleClass().add("study-library-summary"); details.getChildren().addAll(title,summary); HBox.setHgrow(details, Priority.ALWAYS); row.getChildren().add(details);
      row.setOnMouseClicked(e -> { if (e.getButton()==MouseButton.PRIMARY && e.getClickCount()==2 && getItem()!=null) open(getItem()); });
      row.setOnContextMenuRequested(e -> { if(getItem()!=null){showActions(getItem(),e.getSceneX(),e.getSceneY()); e.consume();} }); }
    @Override protected void updateItem(SavedGameSummary game, boolean empty) { super.updateItem(game,empty); if(empty||game==null){setGraphic(null);return;} title.setText(game.whiteName()+" vs "+game.blackName()); summary.setText((game.finished()?game.result().map(Enum::name).orElse("Finished"):"In progress")+" · "+game.movesCount()+" moves · "+game.gameType().name().replace('_',' ')); setGraphic(row); }
  }
}
