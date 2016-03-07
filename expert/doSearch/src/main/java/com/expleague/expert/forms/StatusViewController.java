package com.expleague.expert.forms;

import com.expleague.expert.forms.chat.TimeoutUtil;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.expert.xmpp.events.*;
import com.spbsu.commons.func.Action;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
@SuppressWarnings("unused")
public class StatusViewController {
  public ImageView avatar;
  public ImageView statusView;
  public VBox order;

  private SimpleObjectProperty<ExpertStatus> status = new SimpleObjectProperty<>();

  private final Action<ExpLeagueConnection.Status> statusAction = status -> {
    final String url;
    switch (status) {
      case CONNECTED:
        this.status.set(ExpertStatus.WAITING);
        break;
      case DISCONNECTED:
        this.status.set(ExpertStatus.DISCONNECTED);
        break;
    }
  };
  private Action<ExpertEvent> expertEventHandler = expertEvent -> {
    if (expertEvent instanceof CheckEvent) {
      status.set(ExpertStatus.CHECK);
    }
    else if (expertEvent instanceof CheckCanceledEvent) {
      status.set(ExpertStatus.WAITING);
    }
    else if (expertEvent instanceof TaskInviteCanceledEvent) {
      status.set(ExpertStatus.WAITING);
      Platform.runLater(() -> this.order.getChildren().clear());
    }
    else if (expertEvent instanceof TaskAcceptedEvent) {
      status.set(ExpertStatus.BUSY);
      Platform.runLater(() -> {
        final ExpertTask task = ((ExpertTaskEvent)expertEvent).task();
        final ObservableList<Node> children = this.order.getChildren();
        children.clear();
        final Label topic = new Label(task.offer().topic());
        topic.setStyle("-fx-font-weight: bold");
        children.add(topic);
        final Label state = new Label("В РАБОТЕ");
        state.setStyle("-fx-text-fill: gray; -fx-font-size: 8pt");
        children.add(state);
        final Label timer = new Label();
        TimeoutUtil.setTimer(timer, task.offer().expires(), true);
        children.add(timer);
      });
    }
    else if (expertEvent instanceof TaskSuspendedEvent) {
      status.set(ExpertStatus.WAITING);
      Platform.runLater(() -> order.getChildren().clear());
    }
    else if (expertEvent instanceof TaskInviteEvent) {
      status.set(ExpertStatus.INVITE);
      Platform.runLater(() -> {
        final TaskInviteEvent invite = (TaskInviteEvent) expertEvent;
        final ExpertTask task = invite.task();
        final ObservableList<Node> children = this.order.getChildren();
        children.clear();
        final Label topic = new Label(task.offer().topic());
        topic.setStyle("-fx-font-weight: bold");
        children.add(topic);
        final Label state = new Label("ПРИГЛАШЕНИЕ");
        state.setStyle("-fx-text-fill: gray; -fx-font-size: 8pt");
        children.add(state);
        final Label timer = new Label();
        TimeoutUtil.setTimer(timer, invite.expires(), true);
        children.add(timer);
      });
    }
  };

  private final Action<UserProfile> activeProfile = profile -> {
    final String url;

    if (profile.has(UserProfile.Key.AVATAR_URL)) {
      url = profile.get(UserProfile.Key.AVATAR_URL);
    }
    else {
      url = getClass().getResource("/images/avatar.png").toString();
    }
    profile.expert().addListener(expertEventHandler);
    avatar.setImage(new Image(url));
    updateAvatar();
  };

  @FXML
  public void initialize() {
    status.addListener((observable, oldValue, newValue) -> {
      statusView.setImage(highlighted ? newValue.hIcon() : newValue.icon() );
    });
    statusAction.invoke(ExpLeagueConnection.instance().status());
    ProfileManager.instance().addListener(activeProfile);
    final UserProfile active = ProfileManager.instance().active();
    if (active != null)
      activeProfile.invoke(active);
    updateAvatar();
  }

  private void updateAvatar() {
    Rectangle clip = new Rectangle(
        avatar.getFitWidth(), avatar.getFitHeight()
    );
    clip.setArcWidth(300);
    clip.setArcHeight(300);
    avatar.setClip(clip);

    SnapshotParameters parameters = new SnapshotParameters();
    parameters.setFill(Color.TRANSPARENT);
    WritableImage image = avatar.snapshot(parameters, null);
    avatar.setClip(null);
    avatar.setEffect(new DropShadow(3, Color.BLACK));
    avatar.setImage(image);
  }

  public StatusViewController() {
    ExpLeagueConnection.instance().addListener(statusAction);
    ProfileManager.instance().addListener(activeProfile);
  }

  boolean shown = false;
  public void showConnectionMenu(MouseEvent event) {
    if (shown)
      return;
    shown = true;
    final ExpLeagueConnection connection = ExpLeagueConnection.instance();
    final ContextMenu menu = new ContextMenu(
        createMenuItem("online", "Подключиться", evt -> connection.start(), connection.status() == ExpLeagueConnection.Status.CONNECTED),
        createMenuItem("offline", "Отключиться", evt -> connection.stop(), connection.status() == ExpLeagueConnection.Status.DISCONNECTED)
    );
    final UserProfile active = ProfileManager.instance().active();
    final ExpertTask task = active != null ? active.expert().task() : null;
    if (task != null) {
      menu.getItems().add(new SeparatorMenuItem());
      menu.getItems().addAll(
          createMenuItem("disconnect", "Отказаться от задания", evt -> {
            final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Вы уверены, что хотите отказаться от этого задания?", ButtonType.YES, ButtonType.NO);
            final Optional<ButtonType> type = alert.showAndWait();
            if (type.isPresent() && type.get() == ButtonType.YES)
              task.cancel();
          }, false));
    }
    menu.show(statusView, event.getScreenX() - 180, event.getScreenY());
    highlightStatus(event);
    menu.setOnHiding(evt -> {
      shown = false;
      unhighlightStatus(evt);
    });
  }

  @NotNull
  private MenuItem createMenuItem(final String imageName, String caption, EventHandler<ActionEvent> handler, boolean disable) {
    final Image hIcon = new Image("/images/status/" + imageName + "_h.png");
    final Image icon = new Image("/images/status/" + imageName + ".png");
    HBox graphics = new HBox();
    graphics.setPrefHeight(18);
    final ImageView imageView = new ImageView(icon);
    imageView.setFitHeight(17);
    imageView.setPreserveRatio(true);
    final Region region = new Region();
    HBox.setHgrow(region, Priority.ALWAYS);
    graphics.getChildren().addAll(imageView, region, new Label(caption));
    graphics.setOnMouseEntered(evt -> imageView.setImage(hIcon));
    graphics.setOnMouseExited(evt -> imageView.setImage(icon));
    graphics.setPrefWidth(180);
    final MenuItem item = new MenuItem("", graphics);
    item.setOnAction(handler);
    item.setDisable(disable);
    return item;
  }

  boolean highlighted = false;
  public void highlightStatus(Event event) {
    if (highlighted)
      return;
    statusView.setImage(status.get().hIcon());
    highlighted = true;
  }

  public void unhighlightStatus(Event event) {
    if (!highlighted)
      return;
    if (!shown) {
      statusView.setImage(status.get().icon());
      highlighted = false;
    }
  }

  enum ExpertStatus {
    WAITING("/images/status/online.png"),
    DISCONNECTED("/images/status/offline.png"),
    BUSY("/images/status/play.png"),
    CHECK("/images/status/waiting.png"),
    INVITE("/images/status/new_task.png"),
    ;


    private final Image icon;
    private final Image hIcon;

    ExpertStatus(String icon) {
      this.icon = new Image(icon);
      this.hIcon = new Image(icon.substring(0, icon.length() - 4) + "_h.png");
    }

    private Image icon() {
      return icon;
    }
    private Image hIcon() {
      return hIcon;
    }
  }
}
