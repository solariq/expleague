package com.expleague.expert.forms;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.spbsu.commons.func.Action;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

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

  public void showConnectionMenu(MouseEvent event) {
    final ContextMenu menu = new ContextMenu(new MenuItem("Connect"), new MenuItem("Disconnect"));
    menu.setPrefWidth(150);
    menu.show(status, event.getScreenX() - 150, event.getScreenY());
  }

  public void highlightStatus(Event event) {
    status.setImage(new Image(statusUrl.substring(0, statusUrl.length() - 4) + "_h.png"));
  }

  public void unhighlightStatus(Event event) {
    status.setImage(new Image(statusUrl));
  }
}
