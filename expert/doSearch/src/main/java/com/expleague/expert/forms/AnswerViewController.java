package com.expleague.expert.forms;

import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.Pattern;
import com.expleague.model.Tag;
import com.expleague.model.patch.Patch;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.undo.UndoManager;
import org.markdownwriterfx.Messages;
import org.markdownwriterfx.editor.MarkdownEditorPane;
import org.markdownwriterfx.preview.MarkdownPreviewPane;
import org.markdownwriterfx.util.Action;
import org.markdownwriterfx.util.ActionUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.*;


/**
 * Experts League
 * Created by solar on 04.02.16.
 */
public class AnswerViewController {
  private static final Logger log = Logger.getLogger(AnswerViewController.class.getName());

  public VBox editor;

  private final MarkdownEditorPane editorPane = new MarkdownEditorPane();
  // 'canUndo' property
  private final BooleanProperty canUndo = new SimpleBooleanProperty();
  private StyleClassedTextArea editorNode;

  BooleanProperty canUndoProperty() { return canUndo; }

  // 'canRedo' property
  private final BooleanProperty canRedo = new SimpleBooleanProperty();
  BooleanProperty canRedoProperty() { return canRedo; }
  BooleanProperty activeFileEditorIsNull = new SimpleBooleanProperty(false);

  @FXML
  public void initialize() {
    editor.setUserData(this);
    // Edit actions
    Action editUndoAction = new Action(Messages.get("MainWindow.editUndoAction"), "Shortcut+Z", UNDO,
        e -> editorPane.undo(),
        canUndo.not());
    Action editRedoAction = new Action(Messages.get("MainWindow.editRedoAction"), "Shortcut+Y", REPEAT,
        e -> editorPane.redo(),
        canRedo.not());

    // Insert actions
    Action insertBoldAction = new Action(Messages.get("MainWindow.insertBoldAction"), "Shortcut+B", BOLD,
        e -> editorPane.surroundSelection("**", "**"),
        activeFileEditorIsNull);
    Action insertItalicAction = new Action(Messages.get("MainWindow.insertItalicAction"), "Shortcut+I", ITALIC,
        e -> editorPane.surroundSelection("*", "*"),
        activeFileEditorIsNull);
    Action insertBlockquoteAction = new Action(Messages.get("MainWindow.insertBlockquoteAction"), "Ctrl+Q", QUOTE_LEFT, // not Shortcut+Q because of conflict on Mac
        e -> editorPane.surroundSelection("\n\n> ", ""),
        activeFileEditorIsNull);
    Action insertCodeAction = new Action(Messages.get("MainWindow.insertCodeAction"), "Shortcut+K", CODE,
        e -> editorPane.surroundSelection("`", "`"),
        activeFileEditorIsNull);
    Action insertFencedCodeBlockAction = new Action(Messages.get("MainWindow.insertFencedCodeBlockAction"), "Shortcut+Shift+K", FILE_CODE_ALT,
        e -> editorPane.surroundSelection("\n\n```\n", "\n```\n\n", Messages.get("MainWindow.insertFencedCodeBlockText")),
        activeFileEditorIsNull);

    Action insertLinkAction = new Action(Messages.get("MainWindow.insertLinkAction"), "Shortcut+L", LINK,
        e -> editorPane.insertLink(),
        activeFileEditorIsNull);
    Action insertImageAction = new Action(Messages.get("MainWindow.insertImageAction"), "Shortcut+G", PICTURE_ALT,
        e -> editorPane.insertImage(),
        activeFileEditorIsNull);

    Action insertHeader1Action = new Action(Messages.get("MainWindow.insertHeader1Action"), "Shortcut+1", HEADER,
        e -> editorPane.surroundSelection("\n\n# ", "", Messages.get("MainWindow.insertHeader1Text")),
        activeFileEditorIsNull);

    Action insertUnorderedListAction = new Action(Messages.get("MainWindow.insertUnorderedListAction"), "Shortcut+U", LIST_UL,
        e -> editorPane.surroundSelection("\n\n* ", ""),
        activeFileEditorIsNull);
    Action insertOrderedListAction = new Action(Messages.get("MainWindow.insertOrderedListAction"), "Shortcut+Shift+O", LIST_OL,
        e -> editorPane.surroundSelection("\n\n1. ", ""),
        activeFileEditorIsNull);

    final Action insertPattern = new Action(Messages.get("MainWindow.insertPattern"), "Shortcut+P", MAGIC,
        e -> {
          final ChoiceDialog<Pattern> alert = new ChoiceDialog<>();
          alert.getItems().addAll(ExpLeagueConnection.instance().listPatterns().collect(Collectors.toList()));
          alert.setTitle("Шаблоны");
          alert.setHeaderText("Выберите шаблон");
          alert.showAndWait().ifPresent(p -> {
            editorPane.insertText(p.body());
            task.use(p);
          });
        }, activeFileEditorIsNull);

    //---- ToolBar ----

    ToolBar toolBar = ActionUtils.createToolBar(
        null,
        editUndoAction,
        editRedoAction,
        null,
        insertBoldAction,
        insertItalicAction,
        insertBlockquoteAction,
        insertCodeAction,
        insertFencedCodeBlockAction,
        null,
        insertLinkAction,
        insertImageAction,
        null,
        insertHeader1Action,
        null,
        insertUnorderedListAction,
        insertOrderedListAction,
        insertPattern);

    final UndoManager undoManager =  editorPane.getUndoManager();
    canUndo.bind(undoManager.undoAvailableProperty());
    canRedo.bind(undoManager.redoAvailableProperty());
    editorNode = (StyleClassedTextArea)editorPane.getNode();
    VBox.setVgrow(editorNode, Priority.ALWAYS);
    VBox.setVgrow(toolBar, Priority.NEVER);
    editor.getChildren().addAll(toolBar, editorNode);
    editorPane.pasteAction = this::processClipboard;

    if (task != null) {
      ((StyleClassedTextArea) editorPane.getNode()).setEditable(false);
      editorNode.setOnDragOver(event -> {
        if (checkClipboard(event.getDragboard()))
          event.acceptTransferModes(TransferMode.COPY);
        editorPane.requestFocus();
        final CharacterHit hit = editorNode.hit(event.getX(), event.getY());
        final int insertionIndex = hit.getInsertionIndex();
        editorNode.positionCaret(insertionIndex);
        event.consume();
      });
      editorNode.setOnDragDropped(event -> {
        final Dragboard clipboard = event.getDragboard();
        boolean success = processClipboard(clipboard);
        if (success) {
          editorNode.replaceSelection(clipboard.getString());
          final CharacterHit hit = editorNode.hit(event.getX(), event.getY());
          final int insertionIndex = hit.getInsertionIndex();
          editorNode.positionCaret(insertionIndex);
          event.setDropCompleted(true);
          event.consume();
        }
      });

      editorPane.setMarkdown(task.patchwork());
      editorNode.setEditable(true);
      editorPane.markdownProperty().addListener((observable, oldValue, newValue) -> {
        task.patchwork(newValue);
      });
    }
    else {
      editorPane.setMarkdown(markdown);
      editorNode.setEditable(false);
    }
  }

  private boolean checkClipboard(Clipboard db) {
    return db.hasString() || db.hasImage() ||
        (db.hasFiles() && db.getFiles().size() == 1 && isImage(db.getFiles().get(0).getName())) ||
        (db.hasUrl() && db.getUrl().startsWith("http") && isImage(db.getUrl()));
  }

  private boolean processClipboard(Clipboard db) {
    editorPane.requestFocus();
    String result = null;
    if (db.hasUrl() && db.getUrl().startsWith("http") && isImage(db.getUrl())){
      if (db.hasImage()) {
        result= "![" + (db.hasString() ? db.getString() : "") + "](" + db.getUrl() + ")";
      }
      else {
        result= "[" + (db.hasString() ? db.getString() : "") + "](" + db.getUrl() + ")";
      }
    }
    else if (db.hasFiles() && db.getFiles().size() == 1 && isImage(db.getFiles().get(0).getName())) {
      try {
        result = "![" + (db.hasString() ? db.getString() : "") + "](" +
            ExpLeagueConnection.instance().uploadImage(SwingFXUtils.toFXImage(ImageIO.read(new FileInputStream(db.getFiles().get(0))), null), db.getUrl())
            + ")";
      }
      catch (IOException e) {
        // ignore
      }
    }
    else if (db.hasImage()) {
      result = "![" + (db.hasString() ? db.getString() : "") + "](" +
          ExpLeagueConnection.instance().uploadImage((Image)db.getContent(DataFormat.IMAGE), db.getUrl())
          + ")";
    }
    if (result != null) {
      db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, result));
    }

    return db.hasString();
  }

  private boolean isImage(String file) {
    return Arrays.stream(ImageIO.getReaderFileSuffixes()).anyMatch(file::endsWith);
  }

  private final ExpertTask task;
  private final String markdown;

  public AnswerViewController(ExpertTask task) {
    this.task = task;
    task.editor(this);
    this.markdown = null;
  }

  public AnswerViewController(String markdown) {
    this.task = null;
    this.markdown = markdown;
  }

  public Node createPreview() {
    final MarkdownPreviewPane previewPane = new MarkdownPreviewPane();
    previewPane.markdownASTProperty().bind(editorPane.markdownASTProperty());
    previewPane.scrollYProperty().bind(editorPane.scrollYProperty());
    return previewPane.getNode();
  }

  public void insertAtCursor(Patch patch) {
    editorNode.replaceSelection(patch.toMD() + "\n");
  }
}
