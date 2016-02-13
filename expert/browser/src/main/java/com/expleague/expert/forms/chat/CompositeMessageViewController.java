package com.expleague.expert.forms.chat;

import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.Date;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class CompositeMessageViewController {
  private final VBox root;
  private final DialogueController.MessageType type;
  public VBox contents;
  public AnchorPane parent;

  public CompositeMessageViewController(VBox root, DialogueController.MessageType type) {
    this.root = root;
    this.type = type;
  }

  public DialogueController.MessageType type() {
    return type;
  }

  public void addTimeout(Date expires) {
    final Label timerLabel = new Label();
    timerLabel.setStyle(timerLabel.getStyle() + " -fx-text-fill: lightgray;");
    if (type == DialogueController.MessageType.TASK) {
      contents.getChildren().add(makeCenter(timerLabel));
    }
    else contents.getChildren().add(timerLabel);
    TimeoutUtil.setTimer(timerLabel, expires, false);
  }

  private SimpleDoubleProperty trueWidth = new SimpleDoubleProperty();
  public void addText(String text) {
    final TextArea label = new TextArea();
    final Text labelModel = new Text(text);
    label.getStyleClass().add(type.cssClass());
    label.setText(text);
    label.setEditable(false);
    label.setWrapText(true);
    labelModel.layoutBoundsProperty().addListener(o -> {
      final int value = (int)Math.ceil(labelModel.getLayoutBounds().getHeight() / labelModel.getFont().getSize() / 1.3333);

      if (value > 0) {
        label.setPrefRowCount(value);
        label.setMaxHeight(value * label.getFont().getSize() * 1.3333);
      }
    });

    final InvalidationListener listener = observable -> {
      labelModel.setWrappingWidth(trueWidth.get() - 30);
      label.setMaxWidth(trueWidth.get() - 30);
    };
    trueWidth.addListener(listener);
    listener.invalidated(trueWidth);
    if (type.alignment() == TextAlignment.CENTER)
      contents.getChildren().add(makeCenter(label));
    else
      contents.getChildren().add(label);
  }

  private Node makeCenter(Node flow) {
    final Region left = new Region();
    final Region right = new Region();
    HBox box = new HBox(left, flow, right);
    HBox.setHgrow(left, Priority.ALWAYS);
    HBox.setHgrow(right, Priority.ALWAYS);
    HBox.setHgrow(flow, Priority.NEVER);
    return box;
  }

  @FXML
  public void initialize() {
    root.widthProperty().addListener((observable, oldValue, newValue) -> {
      trueWidth.set(newValue.doubleValue());
    });
    trueWidth.setValue(root.getWidth());
  }
}
