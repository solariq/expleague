package com.expleague.expert.forms;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

/**
 * Created by solar on 06/02/16.
 */
public class Vault {
  public FlowPane vaultContainer;


  @FXML
  public void initialize() {
    vaultContainer.setUserData(this);
  }

  public void append(JsonNode data) {
    if (data.has("link")) {
      final JsonNode link = data.get("link");
      Hyperlink hyperlink = new Hyperlink();
      hyperlink.setUserData(data);
      if (link.has("title"))
        hyperlink.setText(link.get("title").asText());
      hyperlink.setMaxHeight(100);
      hyperlink.setMaxWidth(100);
      hyperlink.setWrapText(true);
      Platform.runLater(() -> vaultContainer.getChildren().add(hyperlink));
    }
    else if (data.has("image")) {
      final JsonNode jsonImage = data.get("image");
      final VBox box = new VBox();
      if (jsonImage.has("title")) {
        final Label title = new Label(jsonImage.get("title").asText());
        title.setFont(Font.font(10));
        title.setStyle("-fx-font-weight: bold");
        box.getChildren().add(title);
      }
      final ImageView image = new ImageView();
      image.setImage(new Image(jsonImage.get("image").asText()));
      image.setFitWidth(80);
      image.setPreserveRatio(true);

      box.setUserData(data);
      box.getChildren().add(image);
      box.setMaxHeight(100);
      box.setMaxWidth(100);
      Platform.runLater(() -> vaultContainer.getChildren().add(box));
    }
    else if (data.has("text")) {
      final JsonNode jsonText = data.get("text");
      final VBox box = new VBox();
      if (jsonText.has("title")) {
        final Label title = new Label(jsonText.get("title").asText());
        title.setFont(Font.font(10));
        title.setStyle("-fx-font-weight: bold");
        box.getChildren().add(title);
      }
      final TextArea area = new TextArea(jsonText.get("text").asText());
      area.setFont(Font.font(10));
      area.setWrapText(true);
      area.setEditable(false);
      box.setUserData(data);
      box.getChildren().add(area);
      box.setMaxHeight(100);
      box.setMaxWidth(100);
      Platform.runLater(() -> vaultContainer.getChildren().add(box));
    }
  }
}
