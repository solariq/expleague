package com.expleague.expert.forms;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.patch.ImagePatch;
import com.expleague.model.patch.LinkPatch;
import com.expleague.model.patch.Patch;
import com.expleague.model.patch.TextPatch;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

/**
 * Experts League
 * Created by solar on 15/02/16.
 */
public class PatchController {
  public Label label;
  public TextArea text;

  private final Patch model;
  private final ExpertTask task;
  public ImageView image;
  public VBox root;

  public PatchController(Patch model, ExpertTask task) {
    this.model = model;
    this.task = task;
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
    root.setOnMouseClicked(event -> {
      if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
        final AnswerViewController editor = task.editor();
        if (editor != null)
          editor.insertAtCursor(model);
        event.consume();
      }
    });
    root.setOnDragDetected(event -> {
      final Dragboard db = root.startDragAndDrop(TransferMode.ANY);
      final ClipboardContent content = new ClipboardContent();
      content.putString(model.toMD());
      db.setContent(content);
      db.setDragView(root.snapshot(null, null), 50, 50);
      event.consume();
    });

  }

  public void delete(Event event) {
    final UserProfile active = ProfileManager.instance().active();
    if (active == null)
      return;
    final ExpertTask task = active.expert().task();
    if (task == null)
      return;
    task.patchesProperty().remove(model);
    if (root.getParent() != null) {
      ((Pane) root.getParent()).getChildren().remove(root);
    }
  }
}
