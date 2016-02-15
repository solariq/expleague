package com.expleague.expert.forms;

import com.expleague.model.patch.ImagePatch;
import com.expleague.model.patch.LinkPatch;
import com.expleague.model.patch.Patch;
import com.expleague.model.patch.TextPatch;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
public class PatchController {
  public Label label;
  public TextArea text;

  private final Patch model;
  public ImageView image;

  public PatchController(Patch model) {
    this.model = model;
  }

  @FXML
  public void initialize() {
    if (model instanceof TextPatch) {
      final TextPatch model = (TextPatch) this.model;
      label.setText(model.title());
      text.setText(model.text());
    }
    else if (model instanceof ImagePatch) {
      final ImagePatch model = (ImagePatch) this.model;
      label.setText(model.title());
      image.setImage(new Image(model.image()));
    }
    else if (model instanceof LinkPatch) {
      final LinkPatch model = (LinkPatch) this.model;
      label.setText(model.title());
      text.setText(model.link());
    }
  }
}
