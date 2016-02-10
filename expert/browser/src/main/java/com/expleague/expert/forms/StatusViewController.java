package com.expleague.expert.forms;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.spbsu.commons.func.Action;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNull;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
@SuppressWarnings("unused")
public class StatusViewController {
  public ImageView avatar;
  public ImageView status;

  private final Action<ExpLeagueConnection.Status> statusAction = status -> {
    final String url;
    switch (status) {
      case CONNECTED:
        url = getClass().getResource("/images/status/waiting.png").toString();
        break;
      case DISCONNECTED:
        url = getClass().getResource("/images/status/disconnect.png").toString();
        break;
      default:
        return;
    }
    statusUrl = url;

    this.status.setImage(new Image(url));
  };
  private String statusUrl = null;

  private final Action<UserProfile> activeProfile = profile -> {
    final String url;

    if (profile.has(UserProfile.Key.AVATAR_URL)) {
      url = profile.get(UserProfile.Key.AVATAR_URL);
    }
    else {
      url = getClass().getResource("/images/avatar.png").toString();
    }
    avatar.setImage(new Image(url));
  };

  @FXML
  public void initialize() {
    final UserProfile active = ProfileManager.instance().active();
    if (active != null)
      activeProfile.invoke(active);
    updateAvatar();
    statusAction.invoke(ExpLeagueConnection.Status.DISCONNECTED);
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
        createMenuItem("online", "подключиться", evt -> connection.start(), connection.status() == ExpLeagueConnection.Status.CONNECTED),
        createMenuItem("offline", "отключиться", evt -> connection.stop(), connection.status() == ExpLeagueConnection.Status.DISCONNECTED)
    );
    menu.setPrefWidth(150);
    menu.show(status, event.getScreenX() - 150, event.getScreenY());
    highlightStatus(event);
    menu.setOnHiding(evt -> {
      shown = false;
      unhighlightStatus(evt);
    });
  }

  @NotNull
  private MenuItem createMenuItem(final String imageName, String caption, EventHandler<ActionEvent> handler, boolean disable) {
    HBox graphics = new HBox();
    graphics.setPrefHeight(18);
    final ImageView imageView = new ImageView(new Image("/images/status/" + imageName + ".png"));
    imageView.setFitHeight(17);
    imageView.setPreserveRatio(true);
    final Region region = new Region();
    HBox.setHgrow(region, Priority.ALWAYS);
    graphics.getChildren().addAll(imageView, region, new Label(caption));
    graphics.setOnMouseEntered(evt -> {
      imageView.setImage(new Image("/images/status/" + imageName + "_h.png"));
    });
    graphics.setOnMouseExited(evt -> {
      imageView.setImage(new Image("/images/status/" + imageName + ".png"));
    });
    graphics.setPrefWidth(130);
    final MenuItem item = new MenuItem("", graphics);
    item.setOnAction(handler);
    item.setDisable(disable);
    return item;
  }

  public void highlightStatus(Event event) {
    status.setImage(new Image(statusUrl.substring(0, statusUrl.length() - 4) + "_h.png"));
  }

  public void unhighlightStatus(Event event) {
    if (!shown)
      status.setImage(new Image(statusUrl));
  }
}
