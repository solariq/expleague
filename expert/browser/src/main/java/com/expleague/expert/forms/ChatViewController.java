package com.expleague.expert.forms;

import com.expleague.expert.forms.chat.CompositeMessageViewController;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpLeagueExpert;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.events.MessageEvent;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.system.RuntimeUtils;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
@SuppressWarnings("unused")
public class ChatViewController implements Action<ExpertEvent> {
  private static final Logger log = Logger.getLogger(ChatViewController.class.getName());
  public TextArea input;
  public VBox messagesView;
  public ScrollPane scroll;
  private String placeHolder = "Напишите клиенту";
  private Text textHolder = new Text();
  private double oldHeight = 0;

  @FXML
  public void initialize() {
    ExpLeagueConnection.instance().expert().addListener(this);
    input.setWrapText(true);
    input.widthProperty().addListener((observable, oldValue, newValue) -> {
      textHolder.setWrappingWidth(input.getWidth() - 17);
    });
    input.focusedProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue && input.getText().equals(placeHolder)) {
        input.setStyle("-fx-text-fill: black;");
        input.setText("");
      }
      if (!newValue && input.getText().isEmpty()) {
        input.setText(placeHolder);
        input.setStyle("-fx-text-fill: lightgray;");
      }
    });
    input.setStyle("-fx-text-fill: lightgray;");
    input.setText(placeHolder);

    textHolder.setFont(input.getFont());
    textHolder.setTextAlignment(TextAlignment.LEFT);
    textHolder.setLineSpacing(2);
    textHolder.textProperty().bind(input.textProperty());
    textHolder.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
      if (oldHeight != newValue.getHeight()) {
        oldHeight = newValue.getHeight();
        input.setPrefHeight(textHolder.getLayoutBounds().getHeight() + 12);
        input.setMinHeight(textHolder.getLayoutBounds().getHeight() + 12);
        input.setMaxHeight(textHolder.getLayoutBounds().getHeight() + 12);
      }
    });
  }
  private final List<CompositeMessageViewController> controllers = new ArrayList<>();
  public void send(String text) {
    final CompositeMessageViewController lastMsgController = controllers.isEmpty() ? null : controllers.get(controllers.size() - 1);
    if (lastMsgController == null || lastMsgController.type() != MessageType.OUTGOING) {
      final ObservableList<Node> children = messagesView.getChildren();
      try {
        final CompositeMessageViewController viewController = new CompositeMessageViewController(MessageType.OUTGOING);
        final Node msg = FXMLLoader.load(getClass().getResource("/forms/chat/outgoing.fxml"), null, null, param -> viewController);
        viewController.addText(text);
        controllers.add(viewController);
        children.add(msg);
      }
      catch (IOException e) {
        log.log(Level.SEVERE, "Unable to load chat element", e);
      }
    }
    else lastMsgController.addText(text);
  }

  public void catchEnter(KeyEvent event) {
    if (event.getCode() == KeyCode.ENTER) {
      if (!event.isControlDown()) {
        send(input.getText());
        input.textProperty().setValue("");
      }
      else {
        final int caretPosition = input.getCaretPosition();
        input.textProperty().setValue(input.getText() + "\n");
        input.positionCaret(caretPosition + 1);
      }
      event.consume();
    }
  }

  public void accept(MessageEvent event) {
    CompositeMessageViewController vc = controllers.isEmpty() ? null : controllers.get(controllers.size() - 1);
    if (vc == null || vc.type() != MessageType.INCOMING) {
      final ObservableList<Node> children = messagesView.getChildren();
      try {
        vc = new CompositeMessageViewController(MessageType.INCOMING);
        final CompositeMessageViewController finalVc = vc;
        final Node msg = FXMLLoader.load(getClass().getResource("/forms/chat/incoming.fxml"), null, null, param -> finalVc);
        controllers.add(vc);
        children.add(msg);
      }
      catch (IOException e) {
        log.log(Level.SEVERE, "Unable to load chat element", e);
      }
    }

    final CompositeMessageViewController finalVc = vc;
    event.visitParts(new MessageEvent.PartsVisitor(){
      @Override
      public void accept(String text) {
        finalVc.addText(text);
      }
    });
  }


  private static RuntimeUtils.InvokeDispatcher dispatcher = new RuntimeUtils.InvokeDispatcher(ChatViewController.class, obj -> {
    log.warning("Unhandled event " + obj);
  }, "accept");
  @Override
  public void invoke(ExpertEvent expertEvent) {
    dispatcher.invoke(this, expertEvent);
  }

  public enum MessageType {
    INCOMING,
    OUTGOING,
  }
}
