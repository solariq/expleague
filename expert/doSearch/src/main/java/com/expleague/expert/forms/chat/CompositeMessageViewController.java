package com.expleague.expert.forms.chat;

import com.expleague.expert.forms.MainController;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.model.Offer;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.javascript.object.*;
import com.spbsu.commons.util.Holder;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.io.IOException;
import java.util.Date;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
public class CompositeMessageViewController {
  private final VBox root;
  private final DialogueController.MessageType type;
  private final JID from;
  private final Image avatarImg;
  public VBox contents;
  public AnchorPane parent;
  public ImageView avatar;

  public CompositeMessageViewController(VBox root, DialogueController.MessageType type, JID from, Image ava) {
    this.root = root;
    this.type = type;
    this.from = from;
    avatarImg = ava;
  }

  public Pane loadOffer(Offer offer) throws IOException {
    final Node taskView = FXMLLoader.load(DialogueController.MessageType.TASK.fxml(), null, null, param -> this);
    addText(offer.topic());
    if (offer.workers().count() > 0) {
      addText("(Продолжение задания)", "comment");
    }
    if (offer.geoSpecific())
      addLocation(offer.location());
    else if (offer.location() != null){
      final Holder<Button> button = new Holder<>();
      button.setValue(addAction("Показать позицию", () -> {
        addLocation(offer.location());
        ((Pane) button.getValue().getParent()).getChildren().remove(button.getValue());
      }));
    }
    for (int i = 0; i < offer.attachments().length; i++) {
      final Item attachment = offer.attachments()[i];
      if (attachment instanceof com.expleague.model.Image) {
        addImage((com.expleague.model.Image)attachment);
      }
    }
    return (Pane)taskView;
  }

  public DialogueController.MessageType type() {
    return type;
  }

  @SuppressWarnings("unused")
  public void addTimeout(Date expires) {
    final Label timerLabel = new Label();
    timerLabel.setStyle(timerLabel.getStyle() + " -fx-text-fill: lightgray;");
    addContentItem(timerLabel, true);
    TimeoutUtil.setTimer(timerLabel, expires, false);
  }

  private SimpleDoubleProperty trueWidth = new SimpleDoubleProperty();

  public void addText(String text, String... cssClass) {
    final TextArea label = new TextArea();
    final Text labelModel2 = new Text(text);
    final Text labelModel = new Text();

    label.getStyleClass().add(type.cssClass());
    label.getStyleClass().addAll(cssClass);
    label.setText(text);
    label.setEditable(false);
    label.setWrapText(true);
    labelModel.setFont(label.getFont());
    labelModel.setLineSpacing(2);
    labelModel.layoutBoundsProperty().addListener(o -> {
      final int value = (int)Math.ceil(labelModel.getLayoutBounds().getHeight() / labelModel.getFont().getSize() / 1.3333);
      if (value > 0) {
//        label.setPrefRowCount(value);
        final double height = value * label.getFont().getSize() * 1.3333;
        final double width = value > 1 ? trueWidth.get() - 30 : labelModel2.getLayoutBounds().getWidth();
        label.resize(width + 4, height + 4);
      }
    });
    labelModel.setText(text);
    label.setManaged(false);
    label.maxHeightProperty().addListener(new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        System.out.println(label.getMaxHeight() + " " + newValue);
//        labelModel.setText(text);
      }
    });

    final InvalidationListener listener = observable -> labelModel.setWrappingWidth(trueWidth.get() - 33);
    trueWidth.addListener(listener);
    addContentItem(new Group(label), false);
  }

  public void addImage(com.expleague.model.Image image) {
    final ImageView imageView = new ImageView(ExpLeagueConnection.instance().load(image));
    imageView.setPreserveRatio(true);
    imageView.setFitWidth(100);
    trueWidth.addListener((observable, oldValue, newValue) -> {
      imageView.setFitWidth((Double)newValue - 50);
    });
    imageView.setOnMouseClicked(event -> {
      try {
        Runtime.getRuntime().exec("open " + image.url());
      }
      catch (IOException e) {
        // ignore
      }
    });

    addContentItem(imageView, false);
  }

  private void addContentItem(Node node, boolean enforceCenter) {
    final Runnable todo = () -> {
      if (type.alignment() == TextAlignment.CENTER || enforceCenter) {
        final Node container = makeCenter(node);
        contents.getChildren().add(container);
      }
      else
        contents.getChildren().add(node);
      updateTrueWidth();
    };
    if (Platform.isFxApplicationThread())
      todo.run();
    else
      Platform.runLater(todo);

  }

  public void addLocation(Offer.Location location) {
    Platform.runLater(() -> {
      final GoogleMapView mapView = new GoogleMapView();
      mapView.addMapInializedListener(() -> {
        //Set the initial properties of the map.
        MapOptions mapOptions = new MapOptions();

        mapOptions.center(new LatLong(location.latitude(), location.longitude()))
            .mapType(MapTypeIdEnum.ROADMAP)
            .overviewMapControl(false)
            .panControl(false)
            .rotateControl(false)
            .scaleControl(false)
            .streetViewControl(false)
            .zoomControl(false)
            .zoom(13);
        final GoogleMap map = mapView.createMap(mapOptions);

        //Add a marker to the map
        MarkerOptions markerOptions = new MarkerOptions();

        markerOptions.position(new LatLong(location.latitude(), location.longitude()))
            .visible(Boolean.TRUE)
            .title("Пользователь");

        Marker marker = new Marker(markerOptions);

        map.addMarker(marker);
      });
      mapView.setMaxHeight(200);
      mapView.setOnMouseClicked(event -> MainController.instance().openMap(location));
      trueWidth.addListener((observable, oldValue, newValue) -> {
        mapView.setMaxWidth((Double) newValue - 50);
      });
      addContentItem(mapView, false);
    });
  }

  public Button addAction(String name, Runnable action) {
    final Button button = new Button(name);
    button.setOnAction(event -> action.run());
    addContentItem(button, true);
    return button;
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
    trueWidth.addListener((observable, oldValue, newValue) -> {
      parent.setPrefWidth((Double)newValue - 5);
    });

    trueWidth.setValue(root.getWidth());
    if (avatar != null) {
      final Circle clip = new Circle(15, 15, 15);
      avatar.setClip(clip);
      if (avatarImg != null)
        avatar.setImage(avatarImg);
    }
  }

  private void updateTrueWidth() {
    trueWidth.setValue(trueWidth.get() - 1);
    trueWidth.setValue(trueWidth.get() + 1);
  }

  public JID from() {
    return from;
  }
}
