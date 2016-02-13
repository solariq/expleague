package com.expleague.expert.forms;

import com.expleague.expert.forms.chat.TimeoutUtil;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.expert.xmpp.events.TaskInviteEvent;
import com.expleague.expert.xmpp.events.TaskStartedEvent;
import com.expleague.expert.xmpp.events.TaskSuspendedEvent;
import com.expleague.model.Offer;
import com.spbsu.commons.func.Action;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 06/02/16.
 */
@SuppressWarnings("Duplicates")
public class MainController implements Action<ExpertEvent> {
  private static final Logger log = Logger.getLogger(MainController.class.getName());
  @FXML
  public ToggleGroup vertical;
  public SplitPane horizontalSplit;
  public SplitPane verticalSplit;
  public HBox toolsPane;
  public Button sendButton;
  public TabPane tabs;
  public ToggleButton dialogueButton;
  public ToggleButton previewButton;
  @FXML
  private Parent vault;
  @FXML
  private Parent dialogue;
  private Action<UserProfile> profileAction = profile -> profile.expert().addListener(MainController.this);

  boolean initialized = false;
  private Node preview;

  @SuppressWarnings("unused")
  @FXML
  public void initialize() {
    if (initialized)
      return;
    initialized = true;
    try {
      toolsPane.getChildren().add(FXMLLoader.load(getClass().getResource("/forms/tools.fxml"), null, null, clazz -> this));
      sendButton.setDisable(true);
    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to load tools bar", e);
    }
    ProfileManager.instance().addListener(profileAction);
    final UserProfile active = ProfileManager.instance().active();
    if (active != null) profileAction.invoke(active);
    vertical.selectedToggleProperty().addListener((obs, prev, next) -> {
      final ObservableList<Node> items = horizontalSplit.getItems();
      if (prev != null)
        prev.setUserData(horizontalSplit.getDividerPositions()[0]);
      items.remove(1, items.size());
      final Toggle toggle = vertical.getSelectedToggle();
      if (toggle == dialogueButton) {
        SplitPane.setResizableWithParent(dialogue, false);
        items.add(dialogue);
        final Double divider = (Double)next.getUserData();
        horizontalSplit.setDividerPosition(0, divider != null ? divider : 0.7);
      }
      else if (toggle == previewButton){
        if (preview == null) {
          final Node editor = tabs.lookup("#editor");
          final AnswerViewController answerVC = (AnswerViewController) editor.getUserData();
          preview = answerVC.createPreview();
          SplitPane.setResizableWithParent(preview, false);
        }
        items.add(preview);
        horizontalSplit.setDividerPosition(0, 1.);
      }
    });
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

  private ExpertTask task;
  @Override
  public void invoke(ExpertEvent expertTaskEvent) {
    if (expertTaskEvent instanceof TaskStartedEvent) {
      task = ((TaskStartedEvent) expertTaskEvent).task();
      vertical.selectToggle(dialogueButton);
      sendButton.setDisable(false);
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
    else if (expertTaskEvent instanceof TaskSuspendedEvent) {
      sendButton.setDisable(true);
      task = null;
    }
  }

  public void sendAnswer(ActionEvent ignore) {
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Лига Экспертов");
    alert.setHeaderText("Закончить работу?");
    alert.setContentText("Уверены, что хотите отослать результат и закончить работу?");

    Optional<ButtonType> result = alert.showAndWait();
    if (result.get() == ButtonType.OK){
      task.answer();
    }
  }
}
