package com.expleague.expert.forms;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;

/**
 * Created by solar on 06/02/16.
 */
@SuppressWarnings("Duplicates")
public class MainController {
  public SplitPane horizontalSplit;
  public SplitPane verticalSplit;
  @FXML
  private Parent vault;
  @FXML
  private Parent dialogue;


  boolean vaultShown = false;
  double lastVaultDividerPosition = 0.8;
  public void hideShowVault(ActionEvent actionEvent) {
    final ObservableList<Node> items = verticalSplit.getItems();
    if (!vaultShown) {
      SplitPane.setResizableWithParent(vault, false);
      items.add(vault);
      verticalSplit.setDividerPosition(0, lastVaultDividerPosition);
      vaultShown = true;
    }
    else {
      lastVaultDividerPosition = verticalSplit.getDividerPositions()[0];
      items.remove(items.size() - 1);
      vaultShown = false;
    }
  }

  boolean dialogueShown = false;
  double lastDialogueDividerPosition = 0.8;
  public void hideShowDialogue(ActionEvent actionEvent) {
    final ObservableList<Node> items = horizontalSplit.getItems();
    if (!dialogueShown) {
      SplitPane.setResizableWithParent(dialogue, false);
      items.add(dialogue);
      horizontalSplit.setDividerPosition(0, lastDialogueDividerPosition);
      dialogueShown = true;
    }
    else {
      lastDialogueDividerPosition = horizontalSplit.getDividerPositions()[0];
      items.remove(items.size() - 1);
      dialogueShown = false;
    }
  }
}
