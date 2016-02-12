package com.expleague.expert.forms;

import com.expleague.expert.forms.chat.TimeoutUtil;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.events.ExpertTaskEvent;
import com.expleague.expert.xmpp.events.TaskInviteEvent;
import com.expleague.expert.xmpp.events.TaskStartedEvent;
import com.expleague.model.Offer;
import com.spbsu.commons.func.Action;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

/**
 * Created by solar on 06/02/16.
 */
@SuppressWarnings("Duplicates")
public class MainController implements Action<ExpertEvent> {
  public SplitPane horizontalSplit;
  public SplitPane verticalSplit;
  @FXML
  private Parent vault;
  @FXML
  private Parent dialogue;
  private Action<UserProfile> profileAction = profile -> profile.expert().addListener(MainController.this);

  @FXML
  public void initialize() {
    ProfileManager.instance().addListener(profileAction);
    final UserProfile active = ProfileManager.instance().active();
    if (active != null) profileAction.invoke(active);
  }

  boolean vaultShown = false;
  double lastVaultDividerPosition = 0.8;
  public void hideShowVault(ActionEvent actionEvent) {
    final ObservableList<Node> items = verticalSplit.getItems();
    if (!vaultShown) {
      SplitPane.setResizableWithParent(vault, false);
      items.add(vault);
      verticalSplit.setDividerPosition(0, lastVaultDividerPosition);
      vaultShown = true;
    }
    else {
      lastVaultDividerPosition = verticalSplit.getDividerPositions()[0];
      items.remove(items.size() - 1);
      vaultShown = false;
    }
  }

  boolean dialogueShown = false;
  double lastDialogueDividerPosition = 0.7;
  public void hideShowDialogue(ActionEvent actionEvent) {
    final ObservableList<Node> items = horizontalSplit.getItems();
    if (!dialogueShown) {
      SplitPane.setResizableWithParent(dialogue, false);
      items.add(dialogue);
      horizontalSplit.setDividerPosition(0, lastDialogueDividerPosition);
      dialogueShown = true;
    }
    else {
      lastDialogueDividerPosition = horizontalSplit.getDividerPositions()[0];
      items.remove(items.size() - 1);
      dialogueShown = false;
    }
  }

  @Override
  public void invoke(ExpertEvent expertTaskEvent) {
    if (expertTaskEvent instanceof TaskStartedEvent) {
      if (!dialogueShown)
        Platform.runLater(() -> hideShowDialogue(null));
    }
    else if (expertTaskEvent instanceof TaskInviteEvent) {
      final TaskInviteEvent inviteEvent = (TaskInviteEvent) expertTaskEvent;

      Platform.runLater(() -> {
        final Offer offer = inviteEvent.task().offer();
        final HBox iconView = new HBox(new ImageView(new Image("/images/avatar.png")));
        iconView.setPadding(new Insets(10, 10, 10, 10));
        Notifications.create()
            .graphic(iconView)
            .title("Лига Экспертов")
            .text("Открыто задание на тему: '" + offer.topic() + "'\n" +
                "Срочность: " + TimeoutUtil.formatPeriodRussian(offer.urgency().time()) + "\n" +
                "Приглашение действует: " + TimeoutUtil.formatPeriodRussian((inviteEvent.expires().getTime() - System.currentTimeMillis())))
            .hideAfter(Duration.seconds(30))
            .position(Pos.TOP_RIGHT)
            .show();
      });
    }
  }
}
