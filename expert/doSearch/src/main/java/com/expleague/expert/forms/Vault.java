package com.expleague.expert.forms;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.expert.xmpp.events.TaskStartedEvent;
import com.expleague.expert.xmpp.events.TaskSuspendedEvent;
import com.expleague.model.patch.ImagePatch;
import com.expleague.model.patch.LinkPatch;
import com.expleague.model.patch.Patch;
import com.expleague.model.patch.TextPatch;
import com.fasterxml.jackson.databind.JsonNode;
import com.spbsu.commons.func.Action;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 06/02/16.
 */
public class Vault implements Action<ExpertEvent> {
  private static final Logger log = Logger.getLogger(Vault.class.getName());
  public FlowPane vaultContainer;
  public ScrollPane vaultScroll;
  private Action<UserProfile> profileAction = profile -> {
    profile.expert().addListener(this);
  };
  private ExpertTask task;


  @FXML
  public void initialize() {
    ProfileManager.instance().addListener(profileAction);
    final UserProfile active = ProfileManager.instance().active();
    if (active != null) {
      active.expert().addListener(this);
    }
    vaultContainer.setUserData(this);
    vaultScroll.widthProperty().addListener(new InvalidationListener() {
      @Override
      public void invalidated(Observable observable) {
        vaultContainer.setPrefWidth(vaultScroll.getWidth() - 10);
      }
    });
    vaultScroll.parentProperty().addListener(observable -> {
      final StackPane parent = (StackPane)vaultScroll.parentProperty().get();
      if (parent != null) {
        vaultScroll.prefWidthProperty().bind(parent.widthProperty());
      }
    });
  }

  @Override
  public void invoke(ExpertEvent expertEvent) {
    if (expertEvent instanceof TaskStartedEvent) {
      task = ((TaskStartedEvent) expertEvent).task();
      Platform.runLater(() -> task.patchesProperty().stream().forEach(this::addPatch));
      task.patchesProperty().addListener((ListChangeListener<Patch>) c -> {
        while (c.next()) {
          if (c.wasAdded()) {
            for (final Patch patch : c.getAddedSubList()) {
              Platform.runLater(() -> addPatch(patch));
            }
          }
        }
      });
    }
    else if (expertEvent instanceof TaskSuspendedEvent) {
      Platform.runLater(() -> vaultContainer.getChildren().clear());
      task = null;
    }
  }

  private void addPatch(final Patch patch) {
    final PatchController controller = new PatchController(patch);
    try {
      final Node load;
      if (patch instanceof TextPatch)
        load = FXMLLoader.load(getClass().getResource("/forms/patch/text-patch.fxml"), null, null, param -> controller);
      else if (patch instanceof ImagePatch)
        load = FXMLLoader.load(getClass().getResource("/forms/patch/image-patch.fxml"), null, null, param -> controller);
      else if (patch instanceof LinkPatch)
        load = FXMLLoader.load(getClass().getResource("/forms/patch/link-patch.fxml"), null, null, param -> controller);
      else
        return;
      load.setOnMouseClicked(event -> {
        if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2 && task != null) {
          final AnswerViewController editor = task.editor();
          if (editor != null)
            editor.insertAtCursor(patch);
        }
      });
      vaultContainer.getChildren().add(load);

    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to load patch view for " + patch, e);
    }
  }

}
