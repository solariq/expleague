package com.expleague.expert.forms;

import com.expleague.expert.forms.chat.CompositeMessageViewController;
import com.expleague.expert.forms.chat.DialogueController;
import com.expleague.expert.forms.chat.TimeoutUtil;
import com.expleague.expert.map.GoogleMap;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.expert.xmpp.events.*;
import com.expleague.model.Answer;
import com.expleague.model.Offer;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.jetbrains.annotations.Nullable;

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
  private static MainController instance;
  public static MainController instance() {
    return instance;
  }
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
  private ScrollPane vault;
  @FXML
  private Parent dialogue;
  private Action<UserProfile> profileAction = profile -> profile.expert().addListener(MainController.this);

  boolean initialized = false;
  private VBox preview;

  @SuppressWarnings("unused")
  @FXML
  public void initialize() {
    if (initialized)
      return;
    initialized = true;
    instance = this;

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
      if (horizontalSplit.getDividerPositions().length > 0)
        prev.setUserData(horizontalSplit.getDividerPositions()[0]);
      items.remove(1, items.size());
      preview = null;
      final Toggle toggle = vertical.getSelectedToggle();
      if (toggle == dialogueButton) {
        SplitPane.setResizableWithParent(dialogue, false);
        items.add(dialogue);
        final Double divider = (Double)next.getUserData();
        horizontalSplit.setDividerPosition(0, divider != null ? divider : 0.7);
      }
      else if (toggle == previewButton){
        final Tab tab = tabs.getSelectionModel().getSelectedItem();
        if (tab != null && tab.getContent().lookup("#editor") != null) {
          selectEditor(tab.getId(), true, false);
        }
        else {
          selectEditor("answer", true, false);
        }
        horizontalSplit.setDividerPosition(0, 1.);
      }
    });

    tabs.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue != null && newValue.getContent().lookup("#editor") != null) {
        selectEditor(newValue.getId(), false, false);
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
  private int editorIndex = 0;
  @Override
  public void invoke(ExpertEvent expertTaskEvent) {
    if (expertTaskEvent instanceof TaskStartedEvent) {
      task = ((TaskStartedEvent) expertTaskEvent).task();
      Platform.runLater(() -> {
        if (task == null)
          return;

        vertical.selectToggle(dialogueButton);
      });
    }
    else if (expertTaskEvent instanceof TaskInviteEvent) {
      final TaskInviteEvent inviteEvent = (TaskInviteEvent) expertTaskEvent;

      Platform.runLater(() -> {
        if (task == null)
          return;
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

        playNotificationSound();
      });
    }
    else if (expertTaskEvent instanceof TaskAcceptedEvent) {
      task = ((TaskAcceptedEvent) expertTaskEvent).task();
      Platform.runLater(() -> {
        sendButton.setDisable(false);
        startEditor(new AnswerViewController(task), "answer", 0, null);
        editorIndex++;
      });
    }
    else if (expertTaskEvent instanceof TaskSuspendedEvent) {
      sendButton.setDisable(true);
      task = null;
      if (preview != null) {
        preview.getChildren().clear();
      }
      Platform.runLater(() -> {
        tabs.getTabs().clear();
//        tabs.getTabs().remove(0, editorIndex);
        editorIndex = 0;
      });
    }
    else if (expertTaskEvent instanceof ChatMessageEvent) {
      final ChatMessageEvent messageEvent = (ChatMessageEvent) expertTaskEvent;
      final Message message = messageEvent.source();
      if (message.has(Answer.class)) {
        final AnswerViewController controller = new AnswerViewController(message.get(Answer.class).value());
        Platform.runLater(() -> startEditor(controller, message.id(), editorIndex++ - 1, message.from()));
      }
    }
  }

  private void playNotificationSound() {
    final Media someSound = new Media(getClass().getResource("/sounds/owl.mp3").toString());
    final MediaPlayer mp = new MediaPlayer(someSound);
    mp.play();
  }

  private void startEditor(AnswerViewController answerController, String id, int index, @Nullable JID from) {
    final Tab tab = new Tab("Ответ " + (from != null ? from.resource() : ""));
    tab.setId(id);
    tab.setClosable(false);
    if (task == null)
      return;
    try {
      tab.setContent(FXMLLoader.load(getClass().getResource("/forms/answer.fxml"), null, null, param -> answerController));
    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to load editor!", e);
    }
    tabs.getTabs().add(index, tab);
  }

  public void selectEditor(String id, boolean showPreview, boolean focus) {
    for (final Tab tab : tabs.getTabs()) {
      if (tab.getId().equals(id)) {
        if (focus)
          tabs.getSelectionModel().select(tab);
        final AnswerViewController answerVC = (AnswerViewController)tab.getContent().getUserData();
        final ObservableList<Node> items = horizontalSplit.getItems();
        if (preview != null)
          items.remove(preview);
        if (showPreview || preview != null) {
          preview = new VBox();
          final CompositeMessageViewController controller = DialogueController.MessageType.TASK.newInstance(preview);
          try {
            final Accordion task = new Accordion(new TitledPane("Задание", controller.loadOffer(this.task.offer())));
            final Node pw = answerVC.createPreview();
            preview.getChildren().addAll(task, pw);
            VBox.setVgrow(task, Priority.NEVER);
            VBox.setVgrow(pw, Priority.ALWAYS);
            preview.setMaxWidth(320);
            preview.setMinWidth(320);
            items.add(this.preview);
            SplitPane.setResizableWithParent(this.preview, false);
          }
          catch (IOException e) {
            log.log(Level.SEVERE, "Unable to create task view", e);
          }
        }
        break;
      }
    }
  }

  public void openMap(Offer.Location location) {
    Tab mapTab = null;
    GoogleMap map = null;
    for (final Tab tab : tabs.getTabs()) {
      if (tab.getContent() instanceof GoogleMap) {
        map = (GoogleMap) tab.getContent();
        mapTab = tab;
        break;
      }
    }
    if (map == null) {
      mapTab = new Tab("Карта");
      tabs.getTabs().add(mapTab);
      mapTab.setContent(map = new GoogleMap());
    }
    map.setMapCenter(location.latitude(), location.longitude());
    map.setMarkerPosition(location.latitude(), location.longitude());
    tabs.getSelectionModel().select(mapTab);
  }

  public void sendAnswer(ActionEvent ignore) {
    final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Лига Экспертов");
    alert.setHeaderText("Закончить работу");
    alert.setContentText("Уверены, что хотите отослать результат и закончить работу над заданием?");

    Optional<ButtonType> result = alert.showAndWait();
    if (result.get() == ButtonType.OK){
      task.answer();
    }
  }
}
