package com.expleague.expert;

import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/**
 * Created by solar on 03/02/16.
 */
public class Browser extends Application {

  @Override
  public void start(Stage stage) {
    GridPane chat = new GridPane();
    chat.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    ColumnConstraints c1 = new ColumnConstraints();
    c1.setPercentWidth(100);
    chat.getColumnConstraints().add(c1);

    for (int i = 0; i < 20; i++) {
      Label chatMessage = new Label("Hi " + i);
      chatMessage.getStyleClass().add("chat-bubble");
      GridPane.setHalignment(chatMessage, i % 2 == 0 ? HPos.LEFT
          : HPos.RIGHT);
      chat.addRow(i, chatMessage);
    }

    ScrollPane scroll = new ScrollPane(chat);
    scroll.setFitToWidth(true);

    Scene scene = new Scene(scroll, 500, 500);
    scene.getStylesheets().add(getClass().getResource("/Test.css").toExternalForm());
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
