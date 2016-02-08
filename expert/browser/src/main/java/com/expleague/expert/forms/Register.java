package com.expleague.expert.forms;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import org.controlsfx.dialog.Wizard;
import org.controlsfx.dialog.WizardPane;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by solar on 06.02.16.
 */
public class Register {
  @FXML
  ToggleGroup registerType;

  public static void register(Stage stage) throws IOException {
    final Register register = new Register();
    final Wizard wizard = new Wizard();
    wizard.setTitle("Поучения профиля пользователя");
    final WizardPane profileType = loadWizardPane(register, "/forms/register/profile.fxml");
    final Wizard.LinearFlow linkProfile = new Wizard.LinearFlow(
        loadWizardPane(register, "/forms/register/login.fxml")
    );
    final Wizard.LinearFlow registerNewProfile = new Wizard.LinearFlow(
        loadWizardPane(register, "/forms/register/create.fxml")
    );
    wizard.setFlow(new Wizard.Flow() {
      Wizard.Flow activeFlow = null;
      @Override
      public Optional<WizardPane> advance(WizardPane wizardPane) {
        if (wizardPane == profileType) {
          final int selectedIndex = register.registerType.getToggles().indexOf(register.registerType.getSelectedToggle());
          if (selectedIndex == 0) {
            activeFlow = registerNewProfile;
          }
          else {
            activeFlow = linkProfile;
          }
        }
        else if (wizardPane == null) {
          return Optional.of(profileType);
        }
        return activeFlow.advance(wizardPane);
      }

      @Override
      public boolean canAdvance(WizardPane wizardPane) {
        return wizardPane == null || wizardPane == profileType || activeFlow.canAdvance(wizardPane);
      }
    });
    wizard.showAndWait();
    if (wizard.resultProperty().get() == ButtonType.CANCEL) {
      System.exit(0);
    }
  }

  @NotNull
  private static WizardPane loadWizardPane(Register register, String fxml) throws IOException {
    WizardPane chooseWhatToDo = new WizardPane();
    Node registrationType = FXMLLoader.load(Register.class.getResource(fxml), null, null, clazz -> register);
    chooseWhatToDo.setContent(registrationType);
    return chooseWhatToDo;
  }

}
