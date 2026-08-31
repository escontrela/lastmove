package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.application.service.LichessArenaService;
import com.escontrela.lastmove.ui.component.context.ContextualMenuPanel;
import com.escontrela.lastmove.ui.component.arena.GameTimelineControl;
import com.escontrela.lastmove.ui.component.arena.ArenaConsoleControl;
import com.escontrela.lastmove.ui.component.arena.BotChallengeSettingsModal;
import com.escontrela.lastmove.ui.component.header.ApplicationHeader;
import com.escontrela.lastmove.ui.component.header.HeaderAction;
import com.escontrela.lastmove.ui.component.toolbar.ToolbarIconButton;
import com.escontrela.lastmove.ui.component.game.LiveGameViewerSource;
import com.escontrela.lastmove.ui.component.game.LiveGameViewerState;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.ui.model.TournamentRowSummary;
import com.escontrela.lastmove.ui.screen.UiFlowManager;
import com.escontrela.lastmove.ui.screen.UiScreenController;
import com.escontrela.lastmove.ui.screen.UiScreenId;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
  private static final DateTimeFormatter CONSOLE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
  private final LichessArenaService arena;
  private final UiFlowManager flow;
  private final SavedGameRepository savedGames;
  private final AnalysisSessionService analyses;
  private final UiEventBus events;
  private final ComputerVsComputerScreenController computerViewer;
  private final LinkedHashMap<String, String> activity = new LinkedHashMap<>();
  private boolean tournamentRefreshInFlight;
  private boolean manualBotChallengeInFlight;
  private BotChallengeConfiguration botConfiguration = BotChallengeConfiguration.defaults();

  @FXML private StackPane root;
  @FXML private ApplicationHeader applicationHeader;
  @FXML private Label connectionLabel, accountLabel, capacityLabel, nowPlayingOpponentLabel, statusLabel, tournamentStateLabel, blitzRatingLabel, rapidRatingLabel, standardRatingLabel;
  @FXML private Button nowPlayingButton;
  @FXML private VBox nowPlayingBox;
  @FXML private Button refreshTournamentsButton;
  @FXML private Button clearChallengesButton;
  @FXML private ToolbarIconButton refreshBotsButton, startBotCycleButton, stopBotCycleButton;
  @FXML private Label botCycleStateLabel;
  @FXML private Label botBaseTimeLabel, botIncrementLabel, botMinimumRatingLabel, botMaximumRatingLabel, botMaximumGamesLabel;
  @FXML private BotChallengeSettingsModal botChallengeSettingsModal;
  @FXML private ArenaConsoleControl challengesConsole, gamesConsole, activityConsole;
  @FXML private ListView<ArenaTournament> tournamentsList;
  @FXML private ListView<LichessBotCandidate> onlineBotsList;
  @FXML private ListView<BotChallengeRow> botChallengeResultsList;
  @FXML private ContextualMenuPanel tournamentContextMenu;
  @FXML private GameTimelineControl gameTimeline;

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
    challengesConsole.setAccessibleHelp("Persisted Lichess challenges and their decisions");
    gamesConsole.setAccessibleHelp("Completed Arena games; select a command to analyse it");
    activityConsole.setAccessibleHelp("Recent Arena events");
    tournamentsList.setAccessibleHelp("Bot-eligible Lichess Arena tournaments. Right-click a tournament for actions.");
    statusLabel.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
    tournamentsList.setPlaceholder(emptyState("No bot tournaments available."));
    onlineBotsList.setPlaceholder(emptyState("No online bots loaded yet."));
    onlineBotsList.setAccessibleHelp("Online Lichess bots. Open a bot's context menu to send a challenge.");
    onlineBotsList.setCellFactory(list -> new BotCell());
    botChallengeResultsList.setCellFactory(list -> new BotChallengeResultCell());
    botChallengeSettingsModal.setOnSave(configuration -> { botConfiguration = configuration; renderBotConfiguration(); refresh(); });
    tournamentsList.setCellFactory(list -> new TournamentCell());
    configureHeaderActions();
  }

  @Override public void onShow() {
    applyCycleConfiguration(arena.botChallengeCycle().configuration());
    refresh();
    refreshTournaments();
    reconcileCurrentGames();
    if (arena.connection().status() == ArenaConnectionStatus.CONNECTED && arena.onlineBots().isEmpty()) {
      refreshOnlineBots();
    }
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
  @FXML public void refreshOnlineBots() {
    if (arena.connection().status() != ArenaConnectionStatus.CONNECTED) { refresh(); return; }
    refreshBotsButton.setDisable(true);
    CompletableFuture.runAsync(arena::refreshOnlineBots).whenComplete((ignored, failure) -> Platform.runLater(() -> { refreshBotsButton.setDisable(false); refresh(); }));
  }
  @FXML public void startBotCycle() {
    try { arena.startBotChallengeCycle(botConfiguration); refresh(); }
    catch (RuntimeException failure) { statusLabel.setText(failure.getMessage()); }
  }
  @FXML public void changeBotChallengeSettings() { botChallengeSettingsModal.show(botConfiguration); }
  @FXML public void stopBotCycle() {
    BotChallengeCycle stopped = arena.stopBotChallengeCycle();
    refresh();
    if (stopped.status() == BotChallengeCycleStatus.STOPPING) {
      statusLabel.setText("Challenge loop stopped. The current game will finish normally.");
    }
  }
  @FXML public void clearChallenges() {
    arena.clearChallenges();
    refresh();
    statusLabel.setText("Challenges log cleared.");
  }
  @FXML public void openNowPlaying() { currentGame().ifPresent(this::openArenaGame); }

  private void openArenaGame(ArenaGame game) {
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
                saved.game().currentClock().whiteRemaining(), saved.game().currentClock().blackRemaining(),
                record.result().isPresent(), record.result(), record.terminationReason(),
                Optional.of("Following Lichess game live")));
        flow.show(UiScreenId.COMPUTER_VS_COMPUTER);
      }
    }, () -> statusLabel.setText("The local game is no longer available."));
  }

  @EventListener public void onArenaEvent(LichessArenaEvent event) {
    String key = event.type() + ":" + event.externalId() + ":" + System.nanoTime();
    activity.put(key, CONSOLE_TIME.format(Instant.now()) + " · " + event.type().name().replace('_', ' ') + " · " + (event.detail() == null ? "" : event.detail()));
    while (activity.size() > 60) activity.remove(activity.keySet().iterator().next());
    try {
      Platform.runLater(this::refresh);
    } catch (IllegalStateException ignored) {
      // Arena can receive a stream event while Spring is up but JavaFX has not built a view yet.
      // State is already durable and will be rendered from it on the next onShow().
    }
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
    if (connectionLabel == null || accountLabel == null || capacityLabel == null || statusLabel == null
        || botCycleStateLabel == null || onlineBotsList == null || botChallengeResultsList == null) {
      return;
    }
    ArenaConnection connection = arena.connection();
    connectionLabel.setText(connection.status().name());
    connectionLabel.getStyleClass().setAll("arena-state", "arena-state-" + connection.status().name().toLowerCase(Locale.ROOT));
    accountLabel.setText(arena.account().map(account -> account.username() + " (Lichess bot)").orElse("Not validated"));
    List<ArenaGame> allGames = arena.activeGames();
    List<ArenaGame> games = effectiveGames();
    List<ArenaGame> playing = allGames.stream().filter(this::isCurrentGame).toList();
    BotChallengeCycle cycle = arena.botChallengeCycle();
    capacityLabel.setText(playing.size() + " / " + arena.maximumConcurrentGames());
    playing.stream().findFirst().ifPresentOrElse(game -> {
      nowPlayingOpponentLabel.setText(opponentName(game, cycle));
      nowPlayingButton.setDisable(false);
      nowPlayingBox.setVisible(true);
      nowPlayingBox.setManaged(true);
    }, () -> cycle.currentGameId().ifPresentOrElse(gameId -> {
      nowPlayingOpponentLabel.setText("Challenge accepted · "
          + cycle.currentBotId().orElse("opponent") + " (syncing game…)");
      nowPlayingButton.setDisable(true);
      nowPlayingBox.setVisible(true);
      nowPlayingBox.setManaged(true);
    }, () -> { nowPlayingOpponentLabel.setText(""); nowPlayingButton.setDisable(true); nowPlayingBox.setVisible(false); nowPlayingBox.setManaged(false); }));
    arena.account().ifPresentOrElse(account -> {
      blitzRatingLabel.setText(ratingText(account.blitzRating()));
      rapidRatingLabel.setText(ratingText(account.rapidRating()));
      standardRatingLabel.setText(ratingText(account.standardRating()));
    }, () -> { blitzRatingLabel.setText("?"); rapidRatingLabel.setText("?"); standardRatingLabel.setText("?"); });
    configureHeaderActions();
    refreshTournamentsButton.setDisable(tournamentRefreshInFlight || connection.status() != ArenaConnectionStatus.CONNECTED);
    refreshBotsButton.setDisable(connection.status() != ArenaConnectionStatus.CONNECTED);
    boolean eligibleBot = arena.onlineBots().stream().anyMatch(bot -> bot.available()
        && bot.rating().map(rating -> rating >= botConfiguration.minimumOpponentRating()
            && rating <= botConfiguration.maximumOpponentRating()).orElse(false));
    startBotCycleButton.setDisable(connection.status() != ArenaConnectionStatus.CONNECTED || cycle.active() || !eligibleBot || manualBotChallengeInFlight);
    stopBotCycleButton.setDisable(!cycle.active() || cycle.status() == BotChallengeCycleStatus.STOPPING);
    List<LichessBotCandidate> visibleBots = filteredOnlineBots();
    onlineBotsList.setPlaceholder(emptyState(visibleBots.isEmpty() && !arena.onlineBots().isEmpty()
        ? "No online bots match the configured rating range." : "No online bots loaded yet."));
    onlineBotsList.setItems(FXCollections.observableArrayList(visibleBots));
    Map<String,String> results = arena.challengeResults();
    botChallengeResultsList.setItems(FXCollections.observableArrayList(results.entrySet().stream()
        .map(entry -> new BotChallengeRow(botName(entry.getKey()), entry.getValue())).toList()));
    botCycleStateLabel.setText(cycle.status()+" · "+cycle.completedGames()+" / "+cycle.configuration().maximumGames()+cycle.currentBotId().map(id -> " · "+id).orElse("")+cycle.stopReason().map(reason -> " — "+reason).orElseGet(() -> arena.onlineBotsError().map(error -> " — "+error).orElse("")));
    challengesConsole.setEntries(arena.challenges().stream().map(challenge ->
        new ArenaConsoleControl.Entry(challengeText(challenge), () -> statusLabel.setText("Challenge " + challenge.id() + ": " + challenge.decision()))).toList(),
        "No challenges received yet.");
    gamesConsole.setEntries(games.stream().map(game ->
        new ArenaConsoleControl.Entry(gameText(game), () -> openArenaGame(game))).toList(), "No completed Arena games yet.");
    gameTimeline.setEntries(games.stream().map(this::timelineEntry).toList());
    List<String> recentActivity = new java.util.ArrayList<>(activity.values());
    java.util.Collections.reverse(recentActivity);
    activityConsole.setEntries(recentActivity.stream().map(item ->
        new ArenaConsoleControl.Entry(item, () -> statusLabel.setText(item))).toList(), "No recent Arena events.");
    tournamentsList.setItems(FXCollections.observableArrayList(arena.tournaments()));
    tournamentStateLabel.setText(tournamentStateText());
    statusLabel.setText(connection.lastError().orElse("Arena ready"));
  }

  private void configureHeaderActions() {
    if (applicationHeader == null) return;
    ArenaConnectionStatus connection = arena.connection().status();
    applicationHeader.setContextActions(List.of(
        new HeaderAction("Connect Knightshade Arena", "Connect Arena", "/images/link_35dp_000000.png", "/images/link_35dp_FFFFFF.png", event -> connect(),
            connection == ArenaConnectionStatus.CONNECTED || connection == ArenaConnectionStatus.CONNECTING),
        new HeaderAction("Disconnect Knightshade Arena", "Disconnect Arena", "/images/link_off_35dp_000000.png", "/images/link_off_35dp_FFFFFF.png", event -> disconnect(),
            connection == ArenaConnectionStatus.DISCONNECTED)));
  }

  private static String ratingText(Optional<Integer> rating) { return rating.map(Object::toString).orElse("?"); }

  private GameTimelineControl.Entry timelineEntry(ArenaGame game) {
    Optional<com.escontrela.lastmove.application.game.SavedGame> saved = game.localGameId().flatMap(this::findSavedSafely);
    String opponent = saved.map(value -> {
      var record = value.game().toRecord();
      String white = record.whitePlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("White");
      String black = record.blackPlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("Black");
      return game.botColor().map(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE ? black : white).orElse(white);
    }).orElseGet(() -> externalOpponent(game).orElse("Opponent pending"));
    String opponentRating = saved.flatMap(value -> value.game().toRecord().whitePlayer()
        .filter(player -> game.botColor().map(color -> color != com.escontrela.lastmove.domain.common.PieceColor.WHITE).orElse(false))
        .flatMap(com.escontrela.lastmove.domain.game.GamePlayer::getElo))
        .or(() -> saved.flatMap(value -> value.game().toRecord().blackPlayer()
            .filter(player -> game.botColor().map(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE).orElse(false))
            .flatMap(com.escontrela.lastmove.domain.game.GamePlayer::getElo)))
        .or(() -> game.challengeId().flatMap(id -> arena.challenges().stream()
        .filter(challenge -> challenge.id().equals(id)).findFirst())
        .flatMap(ArenaChallenge::challengerRating))
        .map(rating -> "Elo " + rating).orElse("");
    GameTimelineControl.Outcome outcome = GameTimelineControl.Outcome.IN_PROGRESS;
    if (saved.isPresent()) {
      var result = saved.get().game().result();
      if (result.isPresent()) {
        boolean botWhite = game.botColor().map(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE).orElse(false);
        outcome = switch (result.get()) {
          case DRAW -> GameTimelineControl.Outcome.DRAWN;
          case WHITE_WINS -> botWhite ? GameTimelineControl.Outcome.WON : GameTimelineControl.Outcome.LOST;
          case BLACK_WINS -> botWhite ? GameTimelineControl.Outcome.LOST : GameTimelineControl.Outcome.WON;
          case UNKNOWN -> GameTimelineControl.Outcome.IN_PROGRESS;
        };
      }
    }
    return new GameTimelineControl.Entry(game.finishedAt().orElse(game.updatedAt()), opponent, opponentRating, outcome,
        () -> openArenaGame(game));
  }

  private List<ArenaGame> effectiveGames() {
    return arena.activeGames().stream()
        .filter(game -> isCurrentGame(game) || (game.status() == ArenaGameStatus.FINISHED
            && game.localGameId().flatMap(this::findSavedSafely).flatMap(saved -> saved.game().result())
                .filter(result -> result != com.escontrela.lastmove.domain.game.GameResult.UNKNOWN).isPresent()
            && externalOpponent(game).filter(name -> !name.isBlank()).isPresent()))
        // Active Lichess games must stay at the top; completed games remain chronological.
        .sorted(java.util.Comparator.comparing(this::isCurrentGame).reversed()
            .thenComparing(ArenaGame::updatedAt, java.util.Comparator.reverseOrder()))
        .toList();
  }

  /** FXML is recreated on navigation, so restore the run configuration owned by the durable cycle. */
  private void applyCycleConfiguration(BotChallengeConfiguration configuration) {
    botConfiguration = configuration;
    renderBotConfiguration();
  }

  private void renderBotConfiguration() {
    botBaseTimeLabel.setText(botConfiguration.clockLimitSeconds() / 60 + " min");
    botIncrementLabel.setText(botConfiguration.clockIncrementSeconds() + " sec");
    botMinimumRatingLabel.setText(Integer.toString(botConfiguration.minimumOpponentRating()));
    botMaximumRatingLabel.setText(Integer.toString(botConfiguration.maximumOpponentRating()));
    botMaximumGamesLabel.setText(Integer.toString(botConfiguration.maximumGames()));
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

  /** Bots shown in the dashboard are limited to the currently configured rating window. */
  private List<LichessBotCandidate> filteredOnlineBots() {
    return arena.onlineBots().stream()
        .filter(bot -> bot.rating().map(rating -> rating >= botConfiguration.minimumOpponentRating()
            && rating <= botConfiguration.maximumOpponentRating()).orElse(false))
        .toList();
  }

  private void showTournamentActions(ArenaTournament tournament, double sceneX, double sceneY) {
    TournamentRowSummary summary = TournamentRowSummary.from(tournament, Instant.now());
    tournamentContextMenu.clearItems();
    tournamentContextMenu.addItem("Register Knight Shade", "", !summary.canRequestRegistration(), event ->
        statusLabel.setText("Tournament registration is prepared; Lichess submission is delivered in Phase 3."));
    tournamentContextMenu.addItem("Refresh tournaments", "", tournamentRefreshInFlight, event -> refreshTournaments());
    tournamentContextMenu.showAtScene(sceneX, sceneY);
  }

  private void showBotActions(LichessBotCandidate bot, double sceneX, double sceneY) {
    BotChallengeCycle cycle = arena.botChallengeCycle();
    boolean disabled = manualBotChallengeInFlight || cycle.active()
        || arena.connection().status() != ArenaConnectionStatus.CONNECTED || !bot.available();
    tournamentContextMenu.clearItems();
    tournamentContextMenu.addItem("Challenge " + bot.username(), "", disabled, event -> challengeBotManually(bot));
    tournamentContextMenu.showAtScene(sceneX, sceneY);
  }

  private void challengeBotManually(LichessBotCandidate bot) {
    manualBotChallengeInFlight = true;
    statusLabel.setText("Sending challenge to " + bot.username() + "…");
    refresh();
    CompletableFuture.supplyAsync(() -> arena.challengeBot(bot, botConfiguration)).whenComplete((submission, failure) ->
        Platform.runLater(() -> {
          manualBotChallengeInFlight = false;
          refresh();
          if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            statusLabel.setText("Could not challenge " + bot.username() + ": " + cause.getMessage());
          } else {
            statusLabel.setText(submission.gameId().isPresent() ? "Game started with " + bot.username() + "." : "Challenge sent to " + bot.username() + ". Waiting for acceptance.");
          }
        }));
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

  private final class BotCell extends ListCell<LichessBotCandidate> {
    private BotCell() {
      getStyleClass().add("arena-bot-cell");
      setOnContextMenuRequested(event -> {
        if (getItem() != null) {
          onlineBotsList.getSelectionModel().select(getItem());
          showBotActions(getItem(), event.getSceneX(), event.getSceneY());
          event.consume();
        }
      });
    }

    @Override protected void updateItem(LichessBotCandidate bot, boolean empty) {
      super.updateItem(bot, empty);
      getStyleClass().removeAll("arena-bot-challenged", "arena-bot-rejected", "arena-bot-friendly");
      boolean friendly = !empty && bot != null && arena.friendlyBotIds().stream()
          .anyMatch(id -> id.equalsIgnoreCase(bot.id()));
      setText(empty || bot == null ? null : bot.username() + (friendly ? " *" : "") + " · "
          + bot.rating().map(Object::toString).orElse("rating unavailable") + " · "
          + (arena.rejectedBotIds().contains(bot.id()) ? "rejected" : arena.challengedBotIds().contains(bot.id()) ? "challenged" : (bot.available() ? "available" : "busy")));
      if (!empty && bot != null) {
        if (arena.rejectedBotIds().contains(bot.id())) getStyleClass().add("arena-bot-rejected");
        else if (arena.challengedBotIds().contains(bot.id())) getStyleClass().add("arena-bot-challenged");
        else if (friendly) getStyleClass().add("arena-bot-friendly");
      }
    }
  }

  private String botName(String id) { return arena.onlineBots().stream().filter(bot -> bot.id().equalsIgnoreCase(id))
      .map(LichessBotCandidate::username).findFirst().orElse(id); }
  private record BotChallengeRow(String botName, String result) {}
  private final class BotChallengeResultCell extends ListCell<BotChallengeRow> {
    @Override protected void updateItem(BotChallengeRow row, boolean empty) {
      super.updateItem(row, empty);
      getStyleClass().removeAll("arena-bot-challenged", "arena-bot-rejected", "arena-bot-accepted");
      setText(empty || row == null ? null : row.botName() + " · " + row.result());
      if (!empty && row != null) {
        if (row.result().startsWith("REJECTED")) getStyleClass().add("arena-bot-rejected");
        else if (row.result().startsWith("ACCEPTED")) getStyleClass().add("arena-bot-accepted");
        else getStyleClass().add("arena-bot-challenged");
      }
    }
  }

  private String challengeText(ArenaChallenge challenge) {
    boolean outgoing = challenge.decision() == ArenaChallengeDecision.SENT;
    String direction = outgoing ? "→ " : "← ";
    return CONSOLE_TIME.format(challenge.decidedAt().orElse(challenge.updatedAt())) + " · " + direction + challenge.challengerName()
        + " · " + challenge.variant() + " · " + (challenge.rated() ? "rated" : "casual") + " · " + challenge.decision()
        + challenge.decisionReason().map(reason -> " — " + reason).orElse("");
  }

  private String gameText(ArenaGame game) {
    String opponent = game.localGameId().flatMap(this::findSavedSafely).map(saved -> {
      var record = saved.game().toRecord();
      String white = record.whitePlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("White");
      String black = record.blackPlayer().map(com.escontrela.lastmove.domain.game.GamePlayer::getName).orElse("Black");
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

  /** A partially written or legacy local record must not take down the JavaFX event loop. */
  private Optional<com.escontrela.lastmove.application.game.SavedGame> findSavedSafely(com.escontrela.lastmove.domain.game.GameId id) {
    try { return savedGames.findSaved(id); }
    catch (RuntimeException failure) { return Optional.empty(); }
  }

  private Optional<String> externalOpponent(ArenaGame game) {
    return game.botColor().flatMap(color -> color == com.escontrela.lastmove.domain.common.PieceColor.WHITE ? game.blackLichessId() : game.whiteLichessId());
  }

  private Optional<ArenaGame> currentGame() {
    return arena.activeGames().stream().filter(this::isCurrentGame)
        .max(java.util.Comparator.comparing(ArenaGame::updatedAt));
  }

  private boolean isCurrentGame(ArenaGame game) {
    return game.status() == ArenaGameStatus.STARTED || game.status() == ArenaGameStatus.ACTIVE
        || game.status() == ArenaGameStatus.STREAM_CLOSED;
  }

  private String opponentName(ArenaGame game, BotChallengeCycle cycle) {
    return externalOpponent(game).or(() -> cycle.currentBotId()).orElse("Opponent pending");
  }

  private Label emptyState(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("session-empty-state");
    return label;
  }
}
