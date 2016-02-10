package com.expleague.expert.forms;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebView;

/**
 * Created by solar on 04.02.16.
 */
public class AnswerViewController {
  public HTMLEditor answerEditor;
  public WebView preview;

  public HtmlEditorListener listener;
  @FXML
  public void initialize() {
    listener = new HtmlEditorListener(answerEditor);
    listener.editedProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue) {
        preview.getEngine().loadContent(answerEditor.getHtmlText());
        listener.editedProperty.setValue(false);
      }
    });
  }

  public class HtmlEditorListener {
    private final BooleanProperty editedProperty;

    private String htmlRef;

    public HtmlEditorListener(final HTMLEditor editor) {
      editedProperty = new SimpleBooleanProperty();
      editedProperty.addListener((ov, o, n) -> htmlRef = n ? null : editor.getHtmlText());
      editedProperty.set(false);

      editor.setOnMouseClicked(e -> checkEdition(editor.getHtmlText()));
      editor.addEventFilter(KeyEvent.KEY_TYPED, e -> checkEdition(editor.getHtmlText()));
    }

    public BooleanProperty editedProperty() {
      return editedProperty;
    }

    private void checkEdition(final String html) {
      if (editedProperty.get()) {
        return;
      }
      editedProperty.set(htmlRef != null
          && html.length() != htmlRef.length()
          || !html.equals(htmlRef));
    }

  }
}
