package com.expleague.expert.forms.chat;

import com.expleague.expert.forms.ChatViewController;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class CompositeMessageViewController {
  private final ChatViewController.MessageType type;
  public VBox contents;
  public AnchorPane parent;

  public CompositeMessageViewController(ChatViewController.MessageType type) {
    this.type = type;
  }

  public ChatViewController.MessageType type() {
    return type;
  }

  public void addText(String text) {
    final Text label = new Text();
    label.setTextAlignment(TextAlignment.LEFT);
    parent.prefWidthProperty().addListener((observable, oldValue, newValue) -> {
      final double trueWidth = newValue.doubleValue();
      label.setWrappingWidth(trueWidth - 35);
    });
    if (parent.getPrefWidth() > 0) {
      label.setWrappingWidth(parent.getPrefWidth() - 35);
    }
    label.setText(text);
    contents.getChildren().add(label);
  }

  private VBox findRoot(Node node) {
    while (node != null && !"dialogue".equals(node.getId())) {
      node = node.getParent();
    }
    return (VBox)node;
  }
  @SuppressWarnings("unused")
  @FXML
  public void initialize() {
    parent.parentProperty().addListener((o, oldParent, newParent) -> {
      final VBox root = findRoot(newParent);
      if (root != null) {
        root.widthProperty().addListener((observable, oldValue, newValue) -> {
          parent.setPrefWidth(newValue.doubleValue());
        });
        parent.setPrefWidth(root.getWidth());
      }
    });
  }
}
