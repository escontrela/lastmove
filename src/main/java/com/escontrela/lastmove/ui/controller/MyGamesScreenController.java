package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.arena.ArenaGame;
import com.escontrela.lastmove.application.arena.ArenaGameStatus;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.CurrentUserService;
import com.escontrela.lastmove.application.service.LichessArenaService;
import com.escontrela.lastmove.application.service.PlayerService;
import com.escontrela.lastmove.application.service.KnightshadeArenaSettingsService;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.ResumeComputerGameEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.player.PlayerSelectorModal;
import com.escontrela.lastmove.ui.component.list.ManagedListCell;
import com.escontrela.lastmove.ui.component.search.RegexSearchControl;
import com.escontrela.lastmove.ui.component.search.RegexSearchFilter;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.fxml.FXML;
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
  private final LichessArenaService arena; private final ComputerVsComputerScreenController computerViewer; private final PlayerService players; private final KnightshadeArenaSettingsService arenaSettings;
  @FXML private StackPane root; @FXML private ListView<SavedGameSummary> gamesList; @FXML private RegexSearchControl regexSearch; @FXML private Label emptyLabel; @FXML private Label gameCountLabel; @FXML private Label statusLabel; @FXML private Button playerLabel; @FXML private ContextualMenuPanel contextualMenuPanel; @FXML private PlayerSelectorModal playerSelector;
  private List<SavedGameSummary> allGames = List.of();
  private Optional<PlayerSummary> selectedPlayer = Optional.empty();
  public MyGamesScreenController(SavedGameRepository games, CurrentUserService currentUser, AnalysisSessionService analyses, UiEventBus events, @Lazy UiFlowManager flow, LichessArenaService arena, @Lazy ComputerVsComputerScreenController computerViewer, PlayerService players, KnightshadeArenaSettingsService arenaSettings) {
    this.games=games; this.currentUser=currentUser; this.analyses=analyses; this.events=events; this.flow=flow; this.arena=arena; this.computerViewer=computerViewer; this.players=players; this.arenaSettings=arenaSettings;
  }
  @FXML public void initialize() { root.getProperties().put("controller",this); gamesList.setCellFactory(v -> new Cell()); regexSearch.setOnSearch(event -> showGames(event.pattern())); playerSelector.setOnPlayerSelected(event -> { selectedPlayer=Optional.of(event.player()); playerLabel.setText(event.player().fullName()); refresh(); }); playerSelector.setOnCancel(event -> statusLabel.setText("Games unchanged")); }
  @Override public void onShow() { synchronizeConfiguredBot(); if (selectedPlayer.isEmpty()) selectedPlayer=currentUser.selectedPlayerId().flatMap(players::playerSummary); playerLabel.setText(selectedPlayer.map(PlayerSummary::fullName).orElse("Choose player")); refresh(); }
  @FXML public void backToHome() { flow.show(UiScreenId.MAIN); }
  @FXML public void choosePlayer() { if (!players.isPersistenceAvailable()) { statusLabel.setText("Player persistence is unavailable."); return; } if (arenaSettings.configuredBotAccount().isEmpty() && arenaSettings.hasBotToken()) { statusLabel.setText("Validating the configured Knightshade Lichess account…"); java.util.concurrent.CompletableFuture.supplyAsync(arenaSettings::validateConfiguredBotAccount).whenComplete((account,failure)->javafx.application.Platform.runLater(()->{ if (failure!=null) { statusLabel.setText("Could not validate Knightshade. Open Settings to check the Lichess token."); return; } players.synchronizeLichessBot(account); showPlayerSelector(); })); return; } showPlayerSelector(); }
  private void showPlayerSelector() { List<PlayerSummary> options=players.listPlayers(); if (options.isEmpty()) { statusLabel.setText("Create or validate a player profile before viewing games."); return; } playerSelector.show(options, selectedPlayer.map(summary->summary.id().value()).orElse(null), "Choose player", "View saved games for an app player or Knightshade. Uncheck to include Lichess participants.", arenaSettings.configuredBotAccount().map(com.escontrela.lastmove.application.arena.LichessBotAccount::id)); }
  private void synchronizeConfiguredBot() { if (players.isPersistenceAvailable()) arenaSettings.configuredBotAccount().ifPresent(players::synchronizeLichessBot); }
  private void refresh() { allGames=selectedPlayer.map(player->games.listSummaries(player.id())).orElse(List.of()); if (regexSearch.isValid()) { regexSearch.submit(); } }
  private void showGames(java.util.regex.Pattern pattern) { List<SavedGameSummary> rows=allGames.stream().filter(game -> RegexSearchFilter.matches(pattern, game.whiteName(), game.blackName(), game.gameType().name(), game.finished() ? "finished" : "in progress", game.result().map(Enum::name).orElse(""))).toList(); gamesList.getItems().setAll(rows); gameCountLabel.setText(rows.size() + (rows.size() == 1 ? " game" : " games")); emptyLabel.setText(allGames.isEmpty() ? "Play a game to find it here." : "No games match this search."); emptyLabel.setVisible(rows.isEmpty()); emptyLabel.setManaged(rows.isEmpty()); statusLabel.setText(rows.isEmpty() ? (allGames.isEmpty() ? "Ready to start your first game" : "No games match the current search") : "Open a game to resume or review it"); }
  private void open(SavedGameSummary game) {
    Optional<ArenaGame> arenaGame = arena.gameForLocal(game.gameId());
    if (arenaGame.isPresent() && arenaGame.get().status() != ArenaGameStatus.FINISHED) { follow(game, arenaGame.get()); return; }
    if (!game.finished()) { flow.show(UiScreenId.HUMAN_VS_COMPUTER); events.publish(new ResumeComputerGameEvent(game.gameId())); return; }
    var session=analyses.createFromGame(games.findSaved(game.gameId()).orElseThrow().game().toRecord());
    events.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Opened saved game")); flow.show(UiScreenId.PGN_ANALYSIS);
  }
  private void showActions(SavedGameSummary game, double x, double y) {
    Optional<ArenaGame> arenaGame = arena.gameForLocal(game.gameId());
    contextualMenuPanel.clearItems();
    if (arenaGame.isPresent()) {
      ArenaGame linked = arenaGame.orElseThrow();
      contextualMenuPanel.addItem(linked.status() == ArenaGameStatus.FINISHED ? "Analyze game" : "Follow game", "", e -> open(game));
      linked.tournamentId().flatMap(id -> arena.tournaments().stream().filter(tournament -> tournament.lichessTournamentId().equals(id)).findFirst())
          .flatMap(tournament -> tournament.url()).ifPresent(url -> contextualMenuPanel.addItem("Open tournament", "", e -> openTournament(url)));
    } else contextualMenuPanel.addItem(game.finished() ? "Review game" : "Resume game", "", e -> open(game));
    contextualMenuPanel.addSeparator();
    contextualMenuPanel.addItem("Delete game…", "", e -> { games.deleteById(game.gameId()); refresh(); statusLabel.setText("Deleted game: " + game.whiteName() + " vs " + game.blackName()); });
    contextualMenuPanel.showAtScene(x, y);
  }
  private void follow(SavedGameSummary summary, ArenaGame game) {
    games.findSaved(summary.gameId()).ifPresentOrElse(saved -> {
      var record = saved.game().toRecord();
      computerViewer.showLichessViewer(game.lichessGameId(), summary.gameId(),
          new com.escontrela.lastmove.ui.component.game.LiveGameViewerState(com.escontrela.lastmove.ui.component.game.LiveGameViewerSource.LICHESS,
              game.tournamentId().flatMap(id -> arena.tournaments().stream().filter(tournament -> tournament.lichessTournamentId().equals(id)).map(tournament -> "Lichess Tournament · " + tournament.name()).findFirst()).orElse("Lichess Arena"),
              record.whitePlayer().orElseThrow(), record.blackPlayer().orElseThrow(), record.initialPosition(), record.currentPosition(),
              record.moves().stream().map(com.escontrela.lastmove.domain.game.RecordedPly::ply).toList(), saved.game().currentClock().whiteRemaining(), saved.game().currentClock().blackRemaining(), record.result().isPresent(), record.result(), record.terminationReason(), Optional.of("Following Lichess game live")));
      flow.show(UiScreenId.COMPUTER_VS_COMPUTER);
    }, () -> statusLabel.setText("The local Lichess game is no longer available."));
  }
  private void openTournament(String url) { try { Desktop.getDesktop().browse(URI.create(url)); } catch (Exception failure) { statusLabel.setText("Could not open the Lichess tournament: " + failure.getMessage()); } }
  private final class Cell extends ManagedListCell<SavedGameSummary> {
    private final HBox row = new HBox(12); private final VBox details = new VBox(4); private final Label title = new Label(); private final Label summary = new Label();
    private Cell() { getStyleClass().add("study-library-cell"); row.getStyleClass().add("study-library-row"); row.setAlignment(Pos.CENTER_LEFT); title.getStyleClass().add("study-library-title"); summary.getStyleClass().add("study-library-summary"); details.getChildren().addAll(title,summary); HBox.setHgrow(details, Priority.ALWAYS); row.getChildren().add(details);
      row.setOnMouseClicked(e -> { if (e.getButton()==MouseButton.PRIMARY && e.getClickCount()==2 && getItem()!=null) open(getItem()); });
      row.setOnContextMenuRequested(e -> { if(getItem()!=null){showActions(getItem(),e.getSceneX(),e.getSceneY()); e.consume();} }); }
    @Override protected void updateItem(SavedGameSummary game, boolean empty) { super.updateItem(game,empty); if(empty||game==null){setGraphic(null);return;} title.setText(game.whiteName()+" vs "+game.blackName()); String tournament=arena.gameForLocal(game.gameId()).flatMap(linked->linked.tournamentId()).flatMap(id->arena.tournaments().stream().filter(item->item.lichessTournamentId().equals(id)).map(item->item.name()).findFirst()).map(name->" · Tournament: "+name).orElse(""); summary.setText((game.finished()?game.result().map(Enum::name).orElse("Finished"):"In progress")+tournament+" · "+game.movesCount()+" moves · "+game.gameType().name().replace('_',' ')); setGraphic(row); }
  }
}
