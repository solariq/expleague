package com.expleague.expert.forms;

import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.model.patch.Patch;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
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

import static de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon.*;


/**
 * Experts League
 * Created by solar on 04.02.16.
 */
public class AnswerViewController {
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
    Action insertStrikethroughAction = new Action(Messages.get("MainWindow.insertStrikethroughAction"), "Shortcut+T", STRIKETHROUGH,
        e -> editorPane.surroundSelection("~~", "~~"),
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
    Action insertHeader2Action = new Action(Messages.get("MainWindow.insertHeader2Action"), "Shortcut+2", HEADER,
        e -> editorPane.surroundSelection("\n\n## ", "", Messages.get("MainWindow.insertHeader2Text")),
        activeFileEditorIsNull);
    Action insertHeader3Action = new Action(Messages.get("MainWindow.insertHeader3Action"), "Shortcut+3", HEADER,
        e -> editorPane.surroundSelection("\n\n### ", "", Messages.get("MainWindow.insertHeader3Text")),
        activeFileEditorIsNull);
    Action insertHeader4Action = new Action(Messages.get("MainWindow.insertHeader4Action"), "Shortcut+4", HEADER,
        e -> editorPane.surroundSelection("\n\n#### ", "", Messages.get("MainWindow.insertHeader4Text")),
        activeFileEditorIsNull);
    Action insertHeader5Action = new Action(Messages.get("MainWindow.insertHeader5Action"), "Shortcut+5", HEADER,
        e -> editorPane.surroundSelection("\n\n##### ", "", Messages.get("MainWindow.insertHeader5Text")),
        activeFileEditorIsNull);
    Action insertHeader6Action = new Action(Messages.get("MainWindow.insertHeader6Action"), "Shortcut+6", HEADER,
        e -> editorPane.surroundSelection("\n\n###### ", "", Messages.get("MainWindow.insertHeader6Text")),
        activeFileEditorIsNull);

    Action insertUnorderedListAction = new Action(Messages.get("MainWindow.insertUnorderedListAction"), "Shortcut+U", LIST_UL,
        e -> editorPane.surroundSelection("\n\n* ", ""),
        activeFileEditorIsNull);
    Action insertOrderedListAction = new Action(Messages.get("MainWindow.insertOrderedListAction"), "Shortcut+Shift+O", LIST_OL,
        e -> editorPane.surroundSelection("\n\n1. ", ""),
        activeFileEditorIsNull);
    Action insertHorizontalRuleAction = new Action(Messages.get("MainWindow.insertHorizontalRuleAction"), "Shortcut+H", null,
        e -> editorPane.surroundSelection("\n\n---\n\n", ""),
        activeFileEditorIsNull);

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
        insertOrderedListAction);

    final UndoManager undoManager =  editorPane.getUndoManager();
    canUndo.bind(undoManager.undoAvailableProperty());
    canRedo.bind(undoManager.redoAvailableProperty());
    editorNode = (StyleClassedTextArea)editorPane.getNode();
    VBox.setVgrow(editorNode, Priority.ALWAYS);
    VBox.setVgrow(toolBar, Priority.NEVER);
    editor.getChildren().addAll(toolBar, editorNode);

    if (task != null) {
      ((StyleClassedTextArea) editorPane.getNode()).setEditable(false);
      editorNode.setOnDragOver(event -> {
        if (event.getDragboard().hasString())
          event.acceptTransferModes(TransferMode.COPY);
        editorPane.requestFocus();
        final CharacterHit hit = editorNode.hit(event.getX(), event.getY());
        final int insertionIndex = hit.getInsertionIndex();
        editorNode.positionCaret(insertionIndex);
        event.consume();
      });
      editorNode.setOnDragDropped(event -> {
        Dragboard db = event.getDragboard();
        boolean success = false;
        editorPane.requestFocus();
        if (db.hasString()) {
          editorNode.replaceSelection(db.getString());
          success = true;
        }
        final CharacterHit hit = editorNode.hit(event.getX(), event.getY());
        final int insertionIndex = hit.getInsertionIndex();
        editorNode.positionCaret(insertionIndex);
        event.setDropCompleted(success);
        event.consume();
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
