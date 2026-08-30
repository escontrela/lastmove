package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.LichessArenaService;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.game.LiveGameViewerSource;
import com.escontrela.lastmove.ui.component.game.LiveGameViewerState;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.TournamentRowSummary;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Arena dashboard. Tournament discovery is delegated to {@link LichessArenaService}. */
@Component
public final class KnightshadeArenaScreenController implements UiScreenController {
  private final LichessArenaService arena;
  private final UiFlowManager flow;
  private final SavedGameRepository savedGames;
  private final AnalysisSessionService analyses;
  private final UiEventBus events;
  private final ComputerVsComputerScreenController computerViewer;
  private final LinkedHashMap<String, String> activity = new LinkedHashMap<>();
  private boolean tournamentRefreshInFlight;

  @FXML private StackPane root;
  @FXML private Label connectionLabel, accountLabel, capacityLabel, statusLabel, tournamentStateLabel;
  @FXML private Button connectButton, disconnectButton, refreshTournamentsButton;
  @FXML private ListView<String> challengesList, gamesList, activityList;
  @FXML private ListView<ArenaTournament> tournamentsList;
  @FXML private ContextualMenuPanel tournamentContextMenu;

  public KnightshadeArenaScreenController(LichessArenaService arena, @Lazy UiFlowManager flow,
      SavedGameRepository savedGames, AnalysisSessionService analyses, UiEventBus events,
      @Lazy ComputerVsComputerScreenController computerViewer) {
    this.arena = arena;
    this.flow = flow;
    this.savedGames = savedGames;
    this.analyses = analyses;
    this.events = events;
    this.computerViewer = computerViewer;
  }

  @FXML public void initialize() {
    root.getProperties().put("controller", this);
    challengesList.setAccessibleHelp("Persisted Lichess challenges and their decisions");
    gamesList.setAccessibleHelp("Arena games; double-click a finished game to analyse it");
    activityList.setAccessibleHelp("Recent Arena events");
    tournamentsList.setAccessibleHelp("Bot-eligible Lichess Arena tournaments. Right-click a tournament for actions.");
    statusLabel.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
    challengesList.setPlaceholder(emptyState("No challenges received yet."));
    gamesList.setPlaceholder(emptyState("No Arena games yet."));
    activityList.setPlaceholder(emptyState("No recent Arena events."));
    tournamentsList.setPlaceholder(emptyState("No bot tournaments available."));
    activityList.setCellFactory(this::activityCell);
    tournamentsList.setCellFactory(list -> new TournamentCell());
  }

  @Override public void onShow() {
    refresh();
    refreshTournaments();
    reconcileCurrentGames();
  }

  @FXML public void connect() {
    try {
      arena.connect();
      refresh();
      refreshTournaments();
    } catch (RuntimeException failure) {
      statusLabel.setText(failure.getMessage());
    }
  }

  @FXML public void disconnect() {
    arena.disconnect();
    refresh();
  }

  @FXML public void refreshTournaments() {
    if (tournamentRefreshInFlight || arena.connection().status() != ArenaConnectionStatus.CONNECTED) {
      refresh();
      return;
    }
    tournamentRefreshInFlight = true;
    refresh();
    CompletableFuture.runAsync(arena::refreshTournaments).whenComplete((ignored, failure) ->
        Platform.runLater(() -> { tournamentRefreshInFlight = false; refresh(); }));
  }

  @FXML public void openSelectedGame(javafx.scene.input.MouseEvent event) {
    if (event.getClickCount() < 2) return;
    int index = gamesList.getSelectionModel().getSelectedIndex();
    List<ArenaGame> games = arena.activeGames();
    if (index < 0 || index >= games.size()) return;
    ArenaGame game = games.get(index);
    if (game.localGameId().isEmpty()) {
      statusLabel.setText("The local game is still being reconciled; try again shortly.");
      return;
    }
    savedGames.findSaved(game.localGameId().orElseThrow()).ifPresentOrElse(saved -> {
      if (game.status() == ArenaGameStatus.FINISHED) {
        var session = analyses.createFromGame(saved.game().toRecord());
        events.publish(new OpenAnalysisSessionEvent(session.sessionId(), "Opened completed Lichess game"));
        flow.show(UiScreenId.PGN_ANALYSIS);
      } else {
        var record = saved.game().toRecord();
        computerViewer.showLichessViewer(game.lichessGameId(), game.localGameId().orElseThrow(),
            new LiveGameViewerState(LiveGameViewerSource.LICHESS, "Lichess Arena",
                record.whitePlayer().orElseThrow(), record.blackPlayer().orElseThrow(),
                record.initialPosition(), record.currentPosition(),
                record.moves().stream().map(com.escontrela.lastmove.domain.game.RecordedPly::ply).toList(),
                saved.game().currentClock().whiteRemaining(), saved.game().currentClock().blackRemaining(), false,
                Optional.of("Following Lichess game live")));
        flow.show(UiScreenId.COMPUTER_VS_COMPUTER);
      }
    }, () -> statusLabel.setText("The local game is no longer available."));
  }

  @EventListener public void onArenaEvent(LichessArenaEvent event) {
    String key = event.type() + ":" + event.externalId();
    activity.remove(key);
    activity.put(key, event.type().name().replace('_', ' ') + " · " + (event.detail() == null ? "" : event.detail()));
    while (activity.size() > 30) activity.remove(activity.keySet().iterator().next());
    Platform.runLater(this::refresh);
  }

  private void reconcileCurrentGames() {
    if (arena.connection().status() != ArenaConnectionStatus.CONNECTED) return;
    CompletableFuture.runAsync(arena::reconcileCurrentGames).whenComplete((ignored, failure) -> Platform.runLater(() -> {
      refresh();
      if (failure != null) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        statusLabel.setText("Could not reconcile Lichess games: " + cause.getMessage());
      }
    }));
  }

  private void refresh() {
    ArenaConnection connection = arena.connection();
    connectionLabel.setText(connection.status().name());
    connectionLabel.getStyleClass().setAll("arena-state", "arena-state-" + connection.status().name().toLowerCase(Locale.ROOT));
    accountLabel.setText(arena.account().map(account -> account.username() + " (Lichess bot)").orElse("Not validated"));
    List<ArenaGame> games = arena.activeGames();
    capacityLabel.setText(games.stream().filter(game -> game.status() == ArenaGameStatus.STARTED || game.status() == ArenaGameStatus.ACTIVE).count() + " / " + arena.maximumConcurrentGames());
    connectButton.setDisable(connection.status() == ArenaConnectionStatus.CONNECTED || connection.status() == ArenaConnectionStatus.CONNECTING);
    disconnectButton.setDisable(connection.status() == ArenaConnectionStatus.DISCONNECTED);
    refreshTournamentsButton.setDisable(tournamentRefreshInFlight || connection.status() != ArenaConnectionStatus.CONNECTED);
    challengesList.setItems(FXCollections.observableArrayList(arena.challenges().stream().map(this::challengeText).toList()));
    gamesList.setItems(FXCollections.observableArrayList(games.stream().map(this::gameText).toList()));
    activityList.setItems(FXCollections.observableArrayList(activity.values()));
    tournamentsList.setItems(FXCollections.observableArrayList(arena.tournaments()));
    tournamentStateLabel.setText(tournamentStateText());
    statusLabel.setText(connection.lastError().orElse("Arena ready"));
  }

  private String tournamentStateText() {
    return switch (arena.tournamentListState()) {
      case DISCONNECTED -> "Connect Arena to load tournaments.";
      case LOADING -> "Loading bot tournaments…";
      case READY -> arena.tournaments().size() + " bot tournament" + (arena.tournaments().size() == 1 ? "" : "s") + " found.";
      case EMPTY -> "No bot-eligible tournaments are currently available.";
      case ERROR -> arena.tournamentListError().orElse("Could not load bot tournaments.");
    };
  }

  private void showTournamentActions(ArenaTournament tournament, double sceneX, double sceneY) {
    TournamentRowSummary summary = TournamentRowSummary.from(tournament, Instant.now());
    tournamentContextMenu.clearItems();
    tournamentContextMenu.addItem("Register Knight Shade", "", !summary.canRequestRegistration(), event ->
        statusLabel.setText("Tournament registration is prepared; Lichess submission is delivered in Phase 3."));
    tournamentContextMenu.addItem("Refresh tournaments", "", tournamentRefreshInFlight, event -> refreshTournaments());
    tournamentContextMenu.showAtScene(sceneX, sceneY);
  }

  private ListCell<String> activityCell(ListView<String> list) {
    return new ListCell<>() {
      private final Label label = new Label();
      { label.setWrapText(true); label.prefWidthProperty().bind(list.widthProperty().subtract(28)); label.getStyleClass().add("arena-activity-entry"); }
      @Override protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        label.setText(empty ? null : item);
        setGraphic(empty ? null : label);
      }
    };
  }

  private final class TournamentCell extends ListCell<ArenaTournament> {
    private final HBox row = new HBox(14);
    private final VBox details = new VBox(3);
    private final Label title = new Label();
    private final Label summary = new Label();
    private final Label registration = new Label();

    private TournamentCell() {
      getStyleClass().add("arena-tournament-cell");
      row.getStyleClass().add("arena-tournament-row");
      row.setAlignment(Pos.CENTER_LEFT);
      title.getStyleClass().add("arena-tournament-title");
      summary.getStyleClass().add("arena-tournament-summary");
      registration.getStyleClass().add("arena-tournament-registration");
      details.getChildren().addAll(title, summary);
      HBox.setHgrow(details, Priority.ALWAYS);
      row.getChildren().addAll(details, registration);
      row.setOnContextMenuRequested(event -> {
        if (getItem() != null) {
          showTournamentActions(getItem(), event.getSceneX(), event.getSceneY());
          event.consume();
        }
      });
    }

    @Override protected void updateItem(ArenaTournament tournament, boolean empty) {
      super.updateItem(tournament, empty);
      if (empty || tournament == null) {
        setGraphic(null);
        return;
      }
      TournamentRowSummary value = TournamentRowSummary.from(tournament, Instant.now());
      title.setText(value.title());
      summary.setText(value.details());
      registration.setText(value.registration());
      registration.getStyleClass().setAll("arena-tournament-registration", "arena-tournament-registration-" + tournament.registrationStatus().name().toLowerCase(Locale.ROOT));
      setGraphic(row);
    }
  }

  private String challengeText(ArenaChallenge challenge) {
    return challenge.challengerName() + " · " + challenge.variant() + " · " + (challenge.rated() ? "rated" : "casual") + " · " + challenge.decision() + challenge.decisionReason().map(reason -> " — " + reason).orElse("");
  }

  private String gameText(ArenaGame game) {
    String opponent = game.localGameId().flatMap(savedGames::findSaved).map(saved -> {
      var record = saved.game().toRecord();
      String white = record.whitePlayer().orElseThrow().getName();
      String black = record.blackPlayer().orElseThrow().getName();
      String name = game.botColor().map(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE ? black : white)
          .orElseGet(() -> { String bot = arena.account().map(LichessBotAccount::username).orElse(""); return bot.equalsIgnoreCase(white) ? black : white; });
      if ("White".equalsIgnoreCase(name) || "Black".equalsIgnoreCase(name)) return externalOpponent(game).orElse(name);
      return name;
    }).or(() -> externalOpponent(game)).orElse("Opponent pending");
    String tournament = game.tournamentId().flatMap(id -> arena.tournaments().stream()
        .filter(candidate -> candidate.lichessTournamentId().equals(id)).map(ArenaTournament::name).findFirst())
        .or(() -> game.tournamentId()).map(name -> " · Tournament: " + name).orElse("");
    return opponent + tournament + " · " + game.status() + " · " + DateTimeFormatter.ISO_INSTANT.format(game.updatedAt()) + game.lastError().map(error -> " — " + error).orElse("");
  }

  private Optional<String> externalOpponent(ArenaGame game) {
    return game.botColor().flatMap(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE ? game.blackLichessId() : game.whiteLichessId());
  }

  private Label emptyState(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("session-empty-state");
    return label;
  }
}
