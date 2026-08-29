package com.escontrela.lastmove.ui.component.game;

import com.escontrela.lastmove.domain.game.GameId;
import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** In-app selector for live Lichess and local computer-versus-computer sessions. */
public final class GameSessionSelectorOverlay extends StackPane {
  public enum Kind { LICHESS, LOCAL_COMPUTERS }
  public record Session(Kind kind,String remoteId,GameId localId,String title,String detail){public String toString(){return title+"\n"+detail;}}
  private final ListView<Session> sessions=new ListView<>(); private Consumer<Session> onOpen=s->{}; private Runnable onNew=()->{},onCancel=()->{};
  public GameSessionSelectorOverlay(){getStyleClass().addAll("message-box-overlay","game-session-selector-overlay");setAlignment(Pos.CENTER);setMaxSize(Double.MAX_VALUE,Double.MAX_VALUE);Label eyebrow=label("Live sessions","eyebrow-label"),title=label("Choose a game","computer-game-setup-title"),copy=label("Continue a local engine match or follow any active Lichess Arena game.","computer-game-setup-description");copy.setWrapText(true);sessions.getStyleClass().add("game-session-selector-list");sessions.setPrefHeight(300);sessions.setCellFactory(list->new ListCell<>(){protected void updateItem(Session item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?null:item.toString());setAccessibleText(empty||item==null?null:item.title()+" "+item.detail());}});Button cancel=button("Cancel","secondary-button"),create=button("New computer match","secondary-button"),open=button("Open selected","primary-button");open.disableProperty().bind(sessions.getSelectionModel().selectedItemProperty().isNull());cancel.setOnAction(e->onCancel.run());create.setOnAction(e->onNew.run());open.setOnAction(e->{Session selected=sessions.getSelectionModel().getSelectedItem();if(selected!=null)onOpen.accept(selected);});sessions.setOnMouseClicked(e->{if(e.getClickCount()==2&&sessions.getSelectionModel().getSelectedItem()!=null)onOpen.accept(sessions.getSelectionModel().getSelectedItem());});Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);VBox card=new VBox(12,eyebrow,title,copy,sessions,new HBox(10,cancel,spacer,create,open));card.setPadding(new Insets(28));card.setMaxWidth(620);card.getStyleClass().add("computer-game-setup-card");getChildren().add(card);setVisible(false);setManaged(false);visibleProperty().addListener((o,a,v)->setManaged(v));}
  public void show(List<Session> values){sessions.setItems(FXCollections.observableArrayList(values));sessions.getSelectionModel().selectFirst();setVisible(true);toFront();Platform.runLater(sessions::requestFocus);}
  public void hide(){setVisible(false);setManaged(false);}
  public void setOnOpen(Consumer<Session> value){onOpen=Objects.requireNonNull(value);}public void setOnNew(Runnable value){onNew=Objects.requireNonNull(value);}public void setOnCancel(Runnable value){onCancel=Objects.requireNonNull(value);}
  private static Label label(String text,String style){Label label=new Label(text);label.getStyleClass().add(style);return label;}private static Button button(String text,String style){Button button=new Button(text);button.getStyleClass().add(style);return button;}
}
