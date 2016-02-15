package com.expleague.expert.forms.chat;

import com.expleague.model.Offer;
import com.lynden.gmapsfx.GoogleMapView;
import com.lynden.gmapsfx.MapComponentInitializedListener;
import com.lynden.gmapsfx.javascript.object.*;
import com.sun.prism.PhongMaterial;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

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
    final Text labelModel = new Text(text);
    label.getStyleClass().add(type.cssClass());
    label.setText(text);
    label.setEditable(false);
    label.setWrapText(true);
    labelModel.layoutBoundsProperty().addListener(o -> {
      final int value = (int)Math.ceil(labelModel.getLayoutBounds().getHeight() / labelModel.getFont().getSize() / 1.3333);

      if (value > 0) {
        label.setPrefRowCount(value);
        label.setMaxHeight(value * label.getFont().getSize() * 1.3333);
      }
    });

    final InvalidationListener listener = observable -> {
      labelModel.setWrappingWidth(trueWidth.get() - 35);
      label.setMaxWidth(trueWidth.get() - 35);
    };
    trueWidth.addListener(listener);
    listener.invalidated(trueWidth);
    if (type.alignment() == TextAlignment.CENTER)
      contents.getChildren().add(makeCenter(label));
    else
      contents.getChildren().add(label);
  }

  public void addImage(Offer.Image image) {
    final ImageView imageView = new ImageView(image.url());
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
    trueWidth.setValue(root.getWidth());
  }
}
