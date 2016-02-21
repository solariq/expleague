package com.expleague.expert.forms.chat;

import com.expleague.model.Offer;
import com.expleague.xmpp.Item;
import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.javascript.object.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
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
  public VBox contents;
  public AnchorPane parent;

  public CompositeMessageViewController(VBox root, DialogueController.MessageType type) {
    this.root = root;
    this.type = type;
  }

  public Node loadOffer(Offer offer) throws IOException {
    final Node taskView = FXMLLoader.load(DialogueController.MessageType.TASK.fxml(), null, null, param -> this);
    addText(offer.topic());
    if (offer.geoSpecific())
      addLocation(offer.location());

    for (int i = 0; i < offer.attachments().length; i++) {
      final Item attachment = offer.attachments()[i];
      if (attachment instanceof com.expleague.model.Image) {
        addImage(((com.expleague.model.Image) attachment));
      }
    }
    return taskView;
  }

  public DialogueController.MessageType type() {
    return type;
  }

  public void addTimeout(Date expires) {
    final Label timerLabel = new Label();
    timerLabel.setStyle(timerLabel.getStyle() + " -fx-text-fill: lightgray;");
    if (type == DialogueController.MessageType.TASK) {
      contents.getChildren().add(makeCenter(timerLabel));
    }
    else contents.getChildren().add(timerLabel);
    TimeoutUtil.setTimer(timerLabel, expires, false);
  }

  private SimpleDoubleProperty trueWidth = new SimpleDoubleProperty();
  public void addText(String text) {
    final TextArea label = new TextArea();
    final Text labelModel2 = new Text(text);
    final Text labelModel = new Text(text);
    label.getStyleClass().add(type.cssClass());
    label.setText(text);
    label.setEditable(false);
    label.setWrapText(true);
    labelModel.setFont(label.getFont());
    labelModel.setLineSpacing(2);
    labelModel.layoutBoundsProperty().addListener(o -> {
      final int value = (int)Math.ceil(labelModel.getLayoutBounds().getHeight() / labelModel.getFont().getSize() / 1.3333);
      if (value > 0) {
        label.setPrefRowCount(value);
        label.setMaxHeight(value * label.getFont().getSize() * 1.3333);
        if (value == 1) {
          label.setMaxWidth(labelModel2.getLayoutBounds().getWidth());
        }
        else {
          label.setMaxWidth(trueWidth.get() - 30);
        }
      }
    });

    final InvalidationListener listener = observable -> {
      labelModel.setWrappingWidth(trueWidth.get() - 30);
    };
    trueWidth.addListener(listener);
    listener.invalidated(trueWidth);
    if (type.alignment() == TextAlignment.CENTER)
      contents.getChildren().add(makeCenter(label));
    else
      contents.getChildren().add(label);
  }

  public void addImage(com.expleague.model.Image image) {
    final Image img = new Image(image.url());
    final ImageView imageView = new ImageView(img);
    imageView.setPreserveRatio(true);
    imageView.setFitWidth(100);
    trueWidth.addListener((observable, oldValue, newValue) -> {
      imageView.setFitWidth((Double)newValue - 50);
    });
    if (type.alignment() == TextAlignment.CENTER)
      contents.getChildren().add(makeCenter(imageView));
    else
      contents.getChildren().add(imageView);
  }

  public void addLocation(Offer.Location location) {

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

      markerOptions.position( new LatLong(location.latitude(), location.longitude()) )
          .visible(Boolean.TRUE)
          .title("Пользователь");

      Marker marker = new Marker( markerOptions );

      map.addMarker(marker);
    });
    mapView.setMaxHeight(200);
    trueWidth.addListener((observable, oldValue, newValue) -> {
      mapView.setMaxWidth((Double)newValue - 50);
    });
    if (type.alignment() == TextAlignment.CENTER)
      contents.getChildren().add(makeCenter(mapView));
    else
      contents.getChildren().add(mapView);
  }


  public void addAction(String name, Runnable action) {
    final Button button = new Button(name);
    button.setOnAction(event -> action.run());
    contents.getChildren().add(button);
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
      root.setPrefWidth((Double)newValue - 2);
    });
    trueWidth.setValue(root.getWidth());
  }
}
