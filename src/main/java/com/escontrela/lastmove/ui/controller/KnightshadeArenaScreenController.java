package com.escontrela.lastmove.ui.controller;

import com.escontrela.lastmove.application.arena.*;
import com.escontrela.lastmove.application.event.LichessArenaEvent;
import com.escontrela.lastmove.application.repository.SavedGameRepository;
import com.escontrela.lastmove.application.service.AnalysisSessionService;
import com.escontrela.lastmove.ui.event.OpenAnalysisSessionEvent;
import com.escontrela.lastmove.ui.event.UiEventBus;
import com.escontrela.lastmove.application.service.LichessArenaService;
import com.escontrela.lastmove.ui.component.game.*;
import com.escontrela.lastmove.ui.screen.*;
import java.util.*;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Arena dashboard; orchestration stays in {@link LichessArenaService}. */
@Component
public final class KnightshadeArenaScreenController implements UiScreenController {
  private final LichessArenaService arena; private final UiFlowManager flow; private final SavedGameRepository savedGames; private final AnalysisSessionService analyses; private final UiEventBus events; private final ComputerVsComputerScreenController computerViewer;
  @FXML private StackPane root; @FXML private Label connectionLabel, accountLabel, capacityLabel, statusLabel;
  @FXML private Button connectButton, disconnectButton; @FXML private ListView<String> challengesList, gamesList, activityList;
  private final LinkedHashMap<String,String> activity = new LinkedHashMap<>();
  public KnightshadeArenaScreenController(LichessArenaService arena, @Lazy UiFlowManager flow, SavedGameRepository savedGames, AnalysisSessionService analyses, UiEventBus events, @Lazy ComputerVsComputerScreenController computerViewer){this.arena=arena;this.flow=flow;this.savedGames=savedGames;this.analyses=analyses;this.events=events;this.computerViewer=computerViewer;}
  @FXML public void initialize(){root.getProperties().put("controller",this); challengesList.setAccessibleHelp("Persisted Lichess challenges and their decisions"); gamesList.setAccessibleHelp("Arena games; double-click a finished game to analyse it"); activityList.setAccessibleHelp("Recent Arena events"); statusLabel.setAccessibleRole(javafx.scene.AccessibleRole.TEXT); challengesList.setPlaceholder(emptyState("No challenges received yet.")); gamesList.setPlaceholder(emptyState("No Arena games yet.")); activityList.setPlaceholder(emptyState("No recent Arena events.")); activityList.setCellFactory(list->new ListCell<>(){private final Label label=new Label();{label.setWrapText(true);label.prefWidthProperty().bind(list.widthProperty().subtract(28));label.getStyleClass().add("arena-activity-entry");} @Override protected void updateItem(String item,boolean empty){super.updateItem(item,empty);label.setText(empty?null:item);setGraphic(empty?null:label);}});}
  @Override public void onShow(){refresh();}
  @FXML public void connect(){try{arena.connect();refresh();}catch(RuntimeException e){statusLabel.setText(e.getMessage());}}
  @FXML public void disconnect(){arena.disconnect();refresh();}
  @FXML public void openSelectedGame(javafx.scene.input.MouseEvent event){if(event.getClickCount()<2)return;int index=gamesList.getSelectionModel().getSelectedIndex();List<ArenaGame> games=arena.activeGames();if(index<0||index>=games.size())return;ArenaGame game=games.get(index);if(game.localGameId().isEmpty()){statusLabel.setText("The local game is still being reconciled; try again shortly.");return;}savedGames.findSaved(game.localGameId().orElseThrow()).ifPresentOrElse(saved->{if(game.status()==ArenaGameStatus.FINISHED){var session=analyses.createFromGame(saved.game().toRecord());events.publish(new OpenAnalysisSessionEvent(session.sessionId(),"Opened completed Lichess game"));flow.show(UiScreenId.PGN_ANALYSIS);}else{var record=saved.game().toRecord();computerViewer.showLichessViewer(game.lichessGameId(),game.localGameId().orElseThrow(),new LiveGameViewerState(LiveGameViewerSource.LICHESS,"Lichess Arena",record.whitePlayer().orElseThrow(),record.blackPlayer().orElseThrow(),record.initialPosition(),record.currentPosition(),record.moves().stream().map(com.escontrela.lastmove.domain.game.RecordedPly::ply).toList(),saved.game().currentClock().whiteRemaining(),saved.game().currentClock().blackRemaining(),false,Optional.of("Following Lichess game live")));flow.show(UiScreenId.COMPUTER_VS_COMPUTER);}},()->statusLabel.setText("The local game is no longer available."));}
  @EventListener public void onArenaEvent(LichessArenaEvent event){String key=event.type()+":"+event.externalId();activity.remove(key);activity.put(key,event.type().name().replace('_',' ')+" · "+(event.detail()==null?"":event.detail()));while(activity.size()>30)activity.remove(activity.keySet().iterator().next());Platform.runLater(this::refresh);}
  private void refresh(){ArenaConnection c=arena.connection();connectionLabel.setText(c.status().name());connectionLabel.getStyleClass().setAll("arena-state", "arena-state-"+c.status().name().toLowerCase(Locale.ROOT));accountLabel.setText(arena.account().map(a->a.username()+" (Lichess bot)").orElse("Not validated"));List<ArenaGame> games=arena.activeGames();capacityLabel.setText(games.stream().filter(g->g.status()==ArenaGameStatus.STARTED||g.status()==ArenaGameStatus.ACTIVE).count()+" / "+arena.maximumConcurrentGames());connectButton.setDisable(c.status()==ArenaConnectionStatus.CONNECTED||c.status()==ArenaConnectionStatus.CONNECTING);disconnectButton.setDisable(c.status()==ArenaConnectionStatus.DISCONNECTED);challengesList.setItems(FXCollections.observableArrayList(arena.challenges().stream().map(this::challengeText).toList()));gamesList.setItems(FXCollections.observableArrayList(games.stream().map(this::gameText).toList()));activityList.setItems(FXCollections.observableArrayList(activity.values()));statusLabel.setText(c.lastError().orElse("Arena ready"));}
  private String challengeText(ArenaChallenge c){return c.challengerName()+" · "+c.variant()+" · "+(c.rated()?"rated":"casual")+" · "+c.decision()+c.decisionReason().map(r->" — "+r).orElse("");}
  private String gameText(ArenaGame g){String opponent=g.localGameId().flatMap(savedGames::findSaved).map(saved->{var record=saved.game().toRecord();String bot=arena.account().map(LichessBotAccount::username).orElse("");String white=record.whitePlayer().orElseThrow().getName(),black=record.blackPlayer().orElseThrow().getName();return bot.equalsIgnoreCase(white)?black:white;}).orElse("Opponent pending");return opponent+" · "+g.status()+" · "+DateTimeFormatter.ISO_INSTANT.format(g.updatedAt())+g.lastError().map(e->" — "+e).orElse("");}
  private Label emptyState(String text){Label label=new Label(text);label.getStyleClass().add("session-empty-state");return label;}
}
