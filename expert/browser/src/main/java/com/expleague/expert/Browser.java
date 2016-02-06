package com.expleague.expert;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Created by solar on 03/02/16.
 */
public class Browser extends Application {

  @Override
  public void start(Stage stage) throws IOException {
    final Parent load = FXMLLoader.load(getClass().getResource("/forms/main.fxml"));
    Scene scene = new Scene(load, 1024, 800);
//    scene.getStylesheets().add(getClass().getResource("/Test.css").toExternalForm());
    stage.setScene(scene);
    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
