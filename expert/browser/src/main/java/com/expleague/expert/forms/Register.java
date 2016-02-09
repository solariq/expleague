package com.expleague.expert.forms;

import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.spbsu.commons.seq.CharSeqTools;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 06.02.16.
 */
public class Register {
  private static final Logger log = Logger.getLogger(Register.class.getName());
  public static final String VK_REDIRECT = "https://oauth.vk.com/blank.html";

  private UserProfile registering;
  @FXML
  public WebView web;
  @FXML
  public ToggleGroup registerType;
  @FXML
  public ToggleGroup socialNetworkType;
  public boolean tokenReceived;
  public TextField server;

  public Register() {
  }

  private Parent loadContent(String fileName) {
    try {
      return FXMLLoader.load(Register.class.getResource("/forms/register/" + fileName), null, null, clazz -> this);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private class ChooseProfilePage extends WizardPage {
    public ChooseProfilePage() {
      super("Выберите способ задания пользователя", loadContent("profile.fxml"));
    }

    @Override
    protected void nextPage() {
      final int selectedIndex = registerType.getToggles().indexOf(registerType.getSelectedToggle());
      getWizard().navTo(selectedIndex == 0 ? index() + 2 : index() + 1);
    }

    @Override
    protected boolean isValid() {
      return true;
    }
  }

  private class LoginPage extends WizardPage {
    public LoginPage() {
      super("Вход известного пользователя", loadContent("login.fxml"));
    }

    @Override
    protected boolean isValid() {
      return tokenReceived;
    }
  }

  private class CreateProfilePage extends WizardPage {
    CreateProfilePage() {
      super("Выберите базовый профиль", loadContent("create.fxml"));
      server.textProperty().addListener(event -> updateButtons());
    }

    @Override
    protected boolean isValid() {
      try {
        registering = new UserProfile(null);
      }
      catch (IOException e) {
        log.log(Level.SEVERE, "", e);
      }
      return !server.textProperty().isEmpty().get();
    }
  }

  private class SocialLoginPage extends WizardPage {
    SocialLoginPage() {
      super("Получение прав от социальной сети", loadContent("social.fxml"));
      try {
        web.getEngine().load("https://oauth.vk.com/authorize?client_id=5270684&display=page&scope=offline,wall&response_type=token&lang=ru&v=5.45&state=IntellijIdeaRulezzz&redirect_uri=" + URLEncoder.encode(VK_REDIRECT, "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
      web.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
        @Override
        public void changed(ObservableValue<? extends Worker.State> observable, Worker.State oldValue, Worker.State newValue) {
          final String location = web.getEngine().getLocation();
          if (location.startsWith(VK_REDIRECT)) {
            web.getEngine().getLoadWorker().stateProperty().removeListener(this);
            final Map<String, String> parameters = CharSeqTools.splitURLQuery(location.substring(VK_REDIRECT.length() + 1));
            if (parameters.containsKey("access_token")) {
              registering.set(UserProfile.Key.EXP_LEAGUE_DOMAIN, server.textProperty().getValue());
              registering.set(UserProfile.Key.VK_TOKEN, parameters.get("access_token"));
              registering.set(UserProfile.Key.VK_USER_ID, parameters.get("user_id"));
              tokenReceived = true;
              web.getEngine().loadContent("<html><body><h3>Права успешно получены</h3></body></html>");
              SocialLoginPage.this.updateButtons();
            }
            else if (parameters.containsKey("error")) {
              log.warning(parameters.get("error"));
              log.warning(parameters.get("error_description"));
              web.getEngine().loadContent(
                  String.format("<html><body><h3>В процессе получения прав произошла ошибка %s по причине %s</h3></body></html>",
                      parameters.get("error"),
                      parameters.get("error_description")
                  ));
            }
          }
        }
      });
    }

    @Override
    protected boolean isValid() {
      return tokenReceived;
    }
  }

  public static void register() throws IOException {
    final Register register = new Register();
    final Wizard wizard = new Wizard(register.new ChooseProfilePage(), register.new LoginPage(), register.new CreateProfilePage(), register.new SocialLoginPage()) {
      @Override
      public void finish() {
        getScene().getWindow().hide();
        if (register.registering != null) {
          final UserProfile profile = ProfileManager.instance().register(register.registering);
          ProfileManager.instance().activate(profile);
        }
      }

      @Override
      public void cancel() {
        System.exit(100);
      }
    };
    final Stage stage = new Stage(StageStyle.DECORATED);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setScene(new Scene(wizard, 720, 550));
    stage.showAndWait();
  }

  /** basic wizard infrastructure class */
  public static class Wizard extends StackPane {
    private static final int UNDEFINED = -1;
    private List<WizardPage> pages = new ArrayList<>();
    private Stack<Integer> history = new Stack<>();
    private int curPageIdx = UNDEFINED;

    Wizard(WizardPage... nodes) {
      pages.addAll(Arrays.asList(nodes));
      navTo(0);
      setStyle("-fx-padding: 10;");
    }

    void nextPage() {
      if (hasNextPage()) {
        navTo(curPageIdx + 1);
      }
    }

    void priorPage() {
      if (hasPriorPage()) {
        navTo(history.pop(), false);
      }
    }

    boolean hasNextPage() {
      return (curPageIdx < pages.size() - 1);
    }

    boolean hasPriorPage() {
      return !history.isEmpty();
    }

    void navTo(int nextPageIdx, boolean pushHistory) {
      if (nextPageIdx < 0 || nextPageIdx >= pages.size())
        throw new ArrayIndexOutOfBoundsException();
      if (curPageIdx != UNDEFINED) {
        if (pushHistory) {
          history.push(curPageIdx);
        }
      }

      WizardPage nextPage = pages.get(nextPageIdx);
      curPageIdx = nextPageIdx;
      getChildren().clear();
      getChildren().add(nextPage);
      nextPage.updateButtons();
    }

    void navTo(int nextPageIdx) {
      navTo(nextPageIdx, true);
    }

    public void finish() {}
    public void cancel() {}

    public int indexOf(WizardPage page) {
      return pages.indexOf(page);
    }
  }

  public static abstract class WizardPage extends VBox {
    Button priorButton   = new Button("_Previous");
    Button nextButton    = new Button("N_ext");
    Button cancelButton  = new Button("Cancel");
    Button finishButton  = new Button("_Finish");

    WizardPage(String title, Parent content) {
      final Label label = new Label(title);
      label.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 5 0;");
      getChildren().add(label);
      setId(title);
      setSpacing(5);
      setStyle("-fx-padding:10; -fx-border-width: 3;");

      Region spring = new Region();
      VBox.setVgrow(spring, Priority.ALWAYS);
      getChildren().addAll(content, spring, getButtons());

      priorButton.setOnAction(actionEvent -> priorPage());
      nextButton.setOnAction(actionEvent -> nextPage());
      cancelButton.setOnAction(actionEvent -> getWizard().cancel());
      finishButton.setOnAction(actionEvent -> getWizard().finish());
    }

    HBox getButtons() {
      Region spring = new Region();
      HBox.setHgrow(spring, Priority.ALWAYS);
      HBox buttonBar = new HBox(5);
      cancelButton.setCancelButton(true);
      buttonBar.getChildren().addAll(spring, priorButton, nextButton, cancelButton, finishButton);
      return buttonBar;
    }

    protected boolean hasNextPage() {
      return getWizard().hasNextPage();
    }

    boolean hasPriorPage() {
      return getWizard().hasPriorPage();
    }

    protected void nextPage() {
      getWizard().nextPage();
    }

    void priorPage() {
      getWizard().priorPage();
    }

    Wizard getWizard() {
      return (Wizard) getParent();
    }

    public int index() {
      return getWizard().indexOf(this);
    }

    public void updateButtons() {
      finishButton.setDisable(true);
      nextButton.setDisable(true);
      if (!hasPriorPage()) {
        priorButton.setDisable(true);
      }
      if (hasNextPage()) {
        nextButton.setDefaultButton(true);
      }
      else {
        finishButton.setDefaultButton(true);
      }

      if (isValid()) {
        if (hasNextPage()) {
          nextButton.setDisable(false);
        }
        else {
          finishButton.setDisable(false);
        }
      }
    }

    protected abstract boolean isValid();
  }
}
