package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.game.SavedGameSummary;
import com.escontrela.lastmove.application.player.PlayerSummary;
import com.escontrela.lastmove.application.service.TagService;
import com.escontrela.lastmove.application.tag.Tag;
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
import com.escontrela.lastmove.ui.component.tag.TagAssignmentControl;
import com.escontrela.lastmove.ui.component.tag.TagDisplayControl;
import com.escontrela.lastmove.ui.component.tag.TagFilterControl;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
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
  private static final DateTimeFormatter GAME_DATE = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.ENGLISH)
      .withZone(ZoneId.systemDefault());
  private final SavedGameRepository games; private final CurrentUserService currentUser;
  private final AnalysisSessionService analyses; private final UiEventBus events; private final UiFlowManager flow;
  private final LichessArenaService arena; private final ComputerVsComputerScreenController computerViewer; private final PlayerService players; private final KnightshadeArenaSettingsService arenaSettings;
  private final TagService tagService;
  @FXML private StackPane root; @FXML private ListView<SavedGameSummary> gamesList; @FXML private RegexSearchControl regexSearch; @FXML private TagFilterControl tagFilter; @FXML private Label emptyLabel; @FXML private Label gameCountLabel; @FXML private Label statusLabel; @FXML private Button playerLabel; @FXML private ContextualMenuPanel contextualMenuPanel; @FXML private PlayerSelectorModal playerSelector;
  private List<SavedGameSummary> allGames = List.of();
  private List<Tag> availableTags = List.of();
  private Map<com.escontrela.lastmove.domain.game.GameId, List<Tag>> tagsByGame = Map.of();
  private Pattern searchPattern = Pattern.compile("");
  private Optional<PlayerSummary> selectedPlayer = Optional.empty();
  public MyGamesScreenController(SavedGameRepository games, CurrentUserService currentUser, AnalysisSessionService analyses, UiEventBus events, @Lazy UiFlowManager flow, LichessArenaService arena, @Lazy ComputerVsComputerScreenController computerViewer, PlayerService players, KnightshadeArenaSettingsService arenaSettings, TagService tagService) {
    this.games=games; this.currentUser=currentUser; this.analyses=analyses; this.events=events; this.flow=flow; this.arena=arena; this.computerViewer=computerViewer; this.players=players; this.arenaSettings=arenaSettings; this.tagService=tagService;
  }
  @FXML public void initialize() { root.getProperties().put("controller",this); gamesList.setCellFactory(v -> new Cell()); regexSearch.setOnSearch(event -> { searchPattern=event.pattern(); showGames(); }); tagFilter.setOnSelectionChanged(ignored -> showGames()); playerSelector.setOnPlayerSelected(event -> { selectedPlayer=Optional.of(event.player()); updatePlayerButton(); refresh(); }); playerSelector.setOnCancel(event -> statusLabel.setText("Games unchanged")); }
  @Override public void onShow() { synchronizeConfiguredBot(); if (selectedPlayer.isEmpty()) selectedPlayer=currentUser.selectedPlayerId().flatMap(players::playerSummary); updatePlayerButton(); refresh(); }
  private void updatePlayerButton() { playerLabel.setText(selectedPlayer.map(PlayerSummary::fullName).orElse("Choose player")); }
  @FXML public void backToHome() { flow.show(UiScreenId.MAIN); }
  @FXML public void choosePlayer() { if (!players.isPersistenceAvailable()) { statusLabel.setText("Player persistence is unavailable."); return; } if (arenaSettings.configuredBotAccount().isEmpty() && arenaSettings.hasBotToken()) { statusLabel.setText("Validating the configured Knightshade Lichess account…"); java.util.concurrent.CompletableFuture.supplyAsync(arenaSettings::validateConfiguredBotAccount).whenComplete((account,failure)->javafx.application.Platform.runLater(()->{ if (failure!=null) { statusLabel.setText("Could not validate Knightshade. Open Settings to check the Lichess token."); return; } players.synchronizeLichessBot(account); showPlayerSelector(); })); return; } showPlayerSelector(); }
  private void showPlayerSelector() { List<PlayerSummary> options=players.listPlayers(); if (options.isEmpty()) { statusLabel.setText("Create or validate a player profile before viewing games."); return; } playerSelector.show(options, selectedPlayer.map(summary->summary.id().value()).orElse(null), "Choose player", "View saved games for an app player or Knightshade. Uncheck to include Lichess participants.", arenaSettings.configuredBotAccount().map(com.escontrela.lastmove.application.arena.LichessBotAccount::id)); }
  private void synchronizeConfiguredBot() { if (players.isPersistenceAvailable()) arenaSettings.configuredBotAccount().ifPresent(players::synchronizeLichessBot); }
  private void refresh() { allGames=selectedPlayer.map(player->games.listSummaries(player.id())).orElse(List.of()); tagsByGame=tagService.tagsForGames(allGames.stream().map(SavedGameSummary::gameId).toList()); availableTags=tagService.availableTags(); tagFilter.setAvailableTags(availableTags); showGames(); }
  private void showGames() { Set<Long> selectedTags=tagFilter.selectedTagIds(); List<SavedGameSummary> rows=allGames.stream().filter(game -> RegexSearchFilter.matches(searchPattern, game.whiteName(), game.blackName(), game.gameType().name(), game.finished() ? "finished" : "in progress", game.result().map(Enum::name).orElse(""))).filter(game -> tagsByGame.getOrDefault(game.gameId(), List.of()).stream().map(Tag::id).collect(java.util.stream.Collectors.toSet()).containsAll(selectedTags)).toList(); gamesList.getItems().setAll(rows); gameCountLabel.setText(rows.size() + (rows.size() == 1 ? " game" : " games")); emptyLabel.setText(allGames.isEmpty() ? "Play a game to find it here." : "No games match this search or tag filter."); emptyLabel.setVisible(rows.isEmpty()); emptyLabel.setManaged(rows.isEmpty()); statusLabel.setText(rows.isEmpty() ? (allGames.isEmpty() ? "Ready to start your first game" : "No games match the current filters") : "Open a game to resume, review or label it"); }
  private void assignTag(SavedGameSummary game, String name) { try { Tag tag=tagService.assignToGame(game.gameId(), name); tagsByGame=new java.util.HashMap<>(tagsByGame); tagsByGame.put(game.gameId(), tagService.tagsFor(TagService.gameTarget(game.gameId()))); availableTags=tagService.availableTags(); tagFilter.setAvailableTags(availableTags); showGames(); statusLabel.setText("Added tag '"+tag.name()+"' to "+game.whiteName()+" vs "+game.blackName()); } catch (IllegalArgumentException failure) { statusLabel.setText(failure.getMessage()); } }
  private void removeTag(SavedGameSummary game, Tag tag) { tagService.removeFromGame(game.gameId(), tag.id()); tagsByGame=new java.util.HashMap<>(tagsByGame); tagsByGame.put(game.gameId(), tagService.tagsFor(TagService.gameTarget(game.gameId()))); showGames(); statusLabel.setText("Removed tag '"+tag.name()+"'"); }
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
    contextualMenuPanel.addSeparator();
    TagAssignmentControl tags = new TagAssignmentControl();
    tags.setAvailableTags(availableTags);
    tags.setAssignedTags(tagsByGame.getOrDefault(game.gameId(), List.of()));
    tags.setOnAssign(name -> { assignTag(game, name); tags.setAvailableTags(availableTags); tags.setAssignedTags(tagsByGame.getOrDefault(game.gameId(), List.of())); });
    tags.setOnRemove(tag -> { removeTag(game, tag); tags.setAssignedTags(tagsByGame.getOrDefault(game.gameId(), List.of())); });
    contextualMenuPanel.addContent(tags);
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
    private final HBox row = new HBox(10);
    private final Label marker = new Label();
    private final VBox details = new VBox(4);
    private final Label title = new Label();
    private final Label context = new Label();
    private final TagDisplayControl tags = new TagDisplayControl();
    private final Label result = new Label();
    private final Label moves = new Label();
    private final Label type = new Label();
    private final Label updated = new Label();
    private final Button action = new Button();

    private Cell() {
      getStyleClass().add("my-games-cell");
      setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
      row.getStyleClass().add("my-games-row"); row.setAlignment(Pos.CENTER_LEFT);
      marker.getStyleClass().add("my-games-marker");
      title.getStyleClass().add("my-games-row-title");
      context.getStyleClass().add("my-games-row-context");
      result.getStyleClass().add("my-games-result");
      moves.getStyleClass().add("my-games-moves");
      type.getStyleClass().add("my-games-type");
      updated.getStyleClass().add("my-games-updated");
      action.getStyleClass().add("my-games-open-button");
      action.setOnAction(event -> { if (getItem() != null) open(getItem()); });
      details.getStyleClass().add("my-games-details");
      details.getChildren().addAll(title, context, tags);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().addAll(marker, details, result, moves, type, updated, action);
      row.setOnMouseClicked(e -> { if (e.getButton()==MouseButton.PRIMARY && e.getClickCount()==2 && getItem()!=null) open(getItem()); });
      row.setOnContextMenuRequested(e -> { if(getItem()!=null){showActions(getItem(),e.getSceneX(),e.getSceneY()); e.consume();} }); }
    @Override protected void updateItem(SavedGameSummary game, boolean empty) {
      super.updateItem(game,empty);
      if(empty||game==null){setGraphic(null);return;}
      title.setText(game.whiteName()+" vs "+game.blackName());
      String tournament=arena.gameForLocal(game.gameId()).flatMap(linked->linked.tournamentId()).flatMap(id->arena.tournaments().stream().filter(item->item.lichessTournamentId().equals(id)).map(item->item.name()).findFirst()).map(name->"Tournament · "+name).orElse(game.finished() ? "Saved game" : "Game in progress");
      context.setText(tournament);
      String resultText = resultText(game);
      marker.setText(resultText.equals("Won") ? "WON" : resultText.equals("Lost") ? "LOST" : resultText.equals("Draw") ? "DRAW" : resultText.equals("In progress") ? "LIVE" : "—");
      marker.getStyleClass().setAll("my-games-marker", resultStyle(game));
      result.setText(game.finished() ? game.result().map(com.escontrela.lastmove.domain.game.GameResult::getPgn).orElse("—") : "—");
      result.getStyleClass().setAll("my-games-score");
      moves.setText(Integer.toString(game.movesCount()));
      type.setText(game.gameType() == com.escontrela.lastmove.application.game.GameType.LICHESS_BOT_TOURNAMENT ? "Lichess Arena" : "Vs computer");
      updated.setText(GAME_DATE.format(game.updatedAt()));
      Optional<ArenaGame> linked = arena.gameForLocal(game.gameId());
      action.setText(linked.filter(item -> item.status() != ArenaGameStatus.FINISHED).isPresent() ? "Follow"
          : game.finished() ? "Review" : "Resume");
      tags.setTags(tagsByGame.getOrDefault(game.gameId(),List.of()));
      setGraphic(row);
    }
  }

  private String resultText(SavedGameSummary game) {
    if (!game.finished()) return "In progress";
    var value = game.result().orElse(com.escontrela.lastmove.domain.game.GameResult.UNKNOWN);
    String player = selectedPlayer.map(PlayerSummary::fullName).orElse("");
    boolean isWhite = player.equalsIgnoreCase(game.whiteName());
    boolean isBlack = player.equalsIgnoreCase(game.blackName());
    if (value == com.escontrela.lastmove.domain.game.GameResult.DRAW) return "Draw";
    if (value == com.escontrela.lastmove.domain.game.GameResult.UNKNOWN) return "Finished";
    boolean playerWon = (isWhite && value == com.escontrela.lastmove.domain.game.GameResult.WHITE_WINS)
        || (isBlack && value == com.escontrela.lastmove.domain.game.GameResult.BLACK_WINS);
    if (isWhite || isBlack) return playerWon ? "Won" : "Lost";
    return value == com.escontrela.lastmove.domain.game.GameResult.WHITE_WINS ? "White won" : "Black won";
  }

  private String resultStyle(SavedGameSummary game) {
    String text = resultText(game);
    if ("Won".equals(text)) return "my-games-result-win";
    if ("Lost".equals(text)) return "my-games-result-loss";
    if ("In progress".equals(text)) return "my-games-result-active";
    return "my-games-result-neutral";
  }
}
