package com.expleague.expert.map;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Worker;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Parent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

@SuppressWarnings("unused")
public class GoogleMap extends Parent {

  public GoogleMap() {
    initMap();
    initCommunication();
    getChildren().add(webView);
    setMarkerPosition(0,0);
    setMapCenter(0, 0);
    switchTerrain();
  }

  private void initMap() {
    webView = new WebView();
    webEngine = webView.getEngine();
    webEngine.load(getClass().getResource("/map/google.html").toExternalForm());
    ready = false;
    webEngine.getLoadWorker().stateProperty().addListener((observableValue, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        ready = true;
      }
    });
  }

  private void initCommunication() {
    webEngine.getLoadWorker().stateProperty().addListener((observableValue, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
        doc = (JSObject) webEngine.executeScript("window");
        doc.setMember("app", GoogleMap.this);
      }
    });
  }

  private void invokeJS(final String str) {
    if(ready) {
      doc.eval(str);
    }
    else {
      webEngine.getLoadWorker().stateProperty().addListener((observableValue, oldState, newState) -> {
        if (newState == Worker.State.SUCCEEDED)
        {
          doc.eval(str);
        }
      });
    }
  }

  public void setOnMapLatLngChanged(EventHandler<MapEvent> eventHandler) {
    onMapLatLngChanged = eventHandler;
  }

  public void handle(double lat, double lng) {
    if(onMapLatLngChanged != null) {
      MapEvent event = new MapEvent(this, lat, lng);
      onMapLatLngChanged.handle(event);
    }
  }

  public void setMarkerPosition(double lat, double lng) {
    String sLat = Double.toString(lat);
    String sLng = Double.toString(lng);
    invokeJS("setMarkerPosition(" + sLat + ", " + sLng + ")");
  }

  public void setMapCenter(double lat, double lng) {
    String sLat = Double.toString(lat);
    String sLng = Double.toString(lng);
    invokeJS("setMapCenter(" + sLat + ", " + sLng + ")");
  }

  public void switchSatellite() {
    invokeJS("switchSatellite()");
  }

  public void switchRoadmap() {
    invokeJS("switchRoadmap()");
  }

  public void switchHybrid() {
    invokeJS("switchHybrid()");
  }

  public void switchTerrain() {
    invokeJS("switchTerrain()");
  }

  public void startJumping() {
    invokeJS("startJumping()");
  }

  public void stopJumping() {
    invokeJS("stopJumping()");
  }

  public void setHeight(double h) {
    webView.setPrefHeight(h);
  }

  public void setWidth(double w) {
    webView.setPrefWidth(w);
  }

  public ReadOnlyDoubleProperty widthProperty() {
    return webView.widthProperty();
  }

  private JSObject doc;
  private EventHandler<MapEvent> onMapLatLngChanged;
  private WebView webView;
  private WebEngine webEngine;
  private boolean ready;

  public class MapEvent extends Event {
    public MapEvent(GoogleMap map, double lat, double lng) {
      super(map, Event.NULL_SOURCE_TARGET, Event.ANY);
      this.lat = lat;
      this.lng = lng;
    }

    public double getLat() {
      return this.lat;
    }

    public double getLng() {
      return this.lng;
    }

    private double lat;
    private double lng;
  }
}