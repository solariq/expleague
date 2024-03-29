package com.expleague.expert.forms.chat;

import com.expleague.expert.forms.MainController;
import com.expleague.expert.profile.ProfileManager;
import com.expleague.expert.profile.UserProfile;
import com.expleague.expert.xmpp.ExpLeagueConnection;
import com.expleague.expert.xmpp.ExpertEvent;
import com.expleague.expert.xmpp.ExpertTask;
import com.expleague.expert.xmpp.events.*;
import com.expleague.model.*;
import com.expleague.model.Image;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.system.RuntimeUtils;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Experts League
 * Created by solar on 09/02/16.
 */
@SuppressWarnings("unused")
public class DialogueController implements Action<ExpertEvent> {
  private static final Logger log = Logger.getLogger(DialogueController.class.getName());
  public VBox taskView;
  public TextArea input;
  public VBox messagesView;
  public VBox root;
  public Accordion taskFolder;
  public VBox taskViewParent;
  public ScrollPane scroll;
  private String placeHolder = "Напишите клиенту";
  private Text textHolder = new Text();
  private double oldHeight = 0;
  private Action<UserProfile> profileChangeListener = profile -> profile.expert().addListener(DialogueController.this);
  private FlowPane tags;
  private FlowPane calls;

  @FXML
  public void initialize() {
    ProfileManager.instance().addListener(profileChangeListener);
    if (ProfileManager.instance().active() != null)
      ProfileManager.instance().active().expert().addListener(this);
    input.setWrapText(true);
    input.widthProperty().addListener((observable, oldValue, newValue) -> {
      textHolder.setWrappingWidth(input.getWidth() - 17);
    });
    input.focusedProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue && input.getText().equals(placeHolder)) {
        input.setStyle("-fx-text-fill: black;");
        input.setText("");
      }
      if (!newValue && input.getText().isEmpty()) {
        input.setText(placeHolder);
        input.setStyle("-fx-text-fill: lightgray;");
      }
    });
    input.setStyle("-fx-text-fill: lightgray;");
    input.setText(placeHolder);
    input.setEditable(false);

    textHolder.setFont(input.getFont());
    textHolder.setTextAlignment(TextAlignment.LEFT);
    textHolder.setLineSpacing(2);
    textHolder.textProperty().bind(input.textProperty());
    textHolder.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
      if (oldHeight != newValue.getHeight()) {
        oldHeight = newValue.getHeight();
        input.setPrefHeight(textHolder.getLayoutBounds().getHeight() + 12);
        input.setMinHeight(textHolder.getLayoutBounds().getHeight() + 12);
        input.setMaxHeight(textHolder.getLayoutBounds().getHeight() + 12);
      }
    });
    root.prefWidthProperty().addListener((observable, oldValue, newValue) -> {
      messagesView.setPrefWidth((Double)newValue);
    });
  }
  private final List<CompositeMessageViewController> controllers = new ArrayList<>();
  public void send(String text) {
    locateVCOfType(MessageType.OUTGOING, task.owner(), task).addText(text);
    task.send(text);
  }

  private CompositeMessageViewController locateVCOfType(MessageType type, JID from, ExpertTask task) {
    final CompositeMessageViewController lastMsgController = controllers.isEmpty() ? null : controllers.get(controllers.size() - 1);
    //noinspection ConstantConditions
    if (lastMsgController instanceof CompositeMessageViewController && lastMsgController.type() == type && lastMsgController.from().equals(from)) {
      return lastMsgController;
    }
    final ObservableList<Node> children = messagesView.getChildren();
    try {
      final JID expertJid = task.offer().workers().filter(
          jid -> jid.local().equals(from.resource())
      ).findAny().orElse(null);
      // todo: fix
      final ExpertsProfile worker = null;
      javafx.scene.image.Image ava = null;
      if (worker != null) {
        ava = new javafx.scene.image.Image(worker.avatar());
      }
      final CompositeMessageViewController viewController = type.newInstance(root, from, ava);
      final Node msg = FXMLLoader.load(type.fxml(), null, null, param -> viewController);
      final int size = children.size();
      Platform.runLater(() -> {
        children.add(msg);
        Platform.runLater(() -> scroll.setVvalue(scroll.getVmax()));
      });
      controllers.add(viewController);
      return viewController;
    }
    catch (IOException e) {
      log.log(Level.SEVERE, "Unable to load chat element!", e);
      throw new RuntimeException(e);
    }
  }

  public void catchEnter(KeyEvent event) {
    if (event.getCode() == KeyCode.ENTER) {
      if (!event.isControlDown()) {
        send(input.getText());
        input.textProperty().setValue("");
      }
      else {
        final int caretPosition = input.getCaretPosition();
        input.textProperty().setValue(input.getText() + "\n");
        input.positionCaret(caretPosition + 1);
      }
      event.consume();
    }
  }

  private ExpertTask task;
  public void accept(TaskStartedEvent taskEvt) {
    this.task = taskEvt.task();
      final ObservableList<Node> children = taskView.getChildren();
      try {
        this.task = taskEvt.task();
        final Offer offer = task.offer();
        final CompositeMessageViewController viewController = MessageType.TASK.newInstance(root, task.client(), null);
        final Pane taskPane = viewController.loadOffer(offer);
        Platform.runLater(() -> {
          children.clear();
          children.add(taskPane);
          taskFolder.setExpandedPane(taskFolder.getPanes().get(0));
        });
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
  }

  public void accept(TaskAcceptedEvent accepted) {
    final HBox topic = new HBox();
    {
      VBox.setVgrow(topic, Priority.NEVER);
      tags = new FlowPane();
      final ImageView plus = createPlus((evt) -> {
        final ChoiceDialog<Tag> alert = new ChoiceDialog<>();
        alert.setTitle("Тематика");
        alert.setHeaderText("Выберите тематику задания");
        final ObservableList<Tag> items = alert.getItems();
        ExpLeagueConnection.instance().listTags().filter(tag -> !task.tags().contains(tag)).forEach(items::add);
        alert.showAndWait().ifPresent(task::tag);
      });
      final Text label = new Text("Тематика: ");
      tags.getChildren().add(plus);
      topic.getChildren().addAll(label, tags);
    }

    final HBox calls = new HBox();
    {
      VBox.setVgrow(calls, Priority.NEVER);
      final ImageView plus = createPlus((evt) -> {
        final TextInputDialog alert = new TextInputDialog();
        alert.setTitle("Звонок");
        alert.setHeaderText("По номеру:");
        alert.showAndWait().ifPresent(task::call);
      });
      final Text label = new Text("Звонки: ");
      this.calls = new FlowPane();
      this.calls.getChildren().add(plus);
      calls.getChildren().addAll(label, this.calls);
    }
    final VBox toolsBox = new VBox();
    toolsBox.getChildren().addAll(topic, calls);
    Platform.runLater(() -> taskViewParent.getChildren().add(toolsBox));
  }

  @NotNull
  private ImageView createPlus(EventHandler<MouseEvent> onclick) {
    final ImageView plus = new ImageView(new javafx.scene.image.Image("/images/add_tag.png"));
    plus.setFitWidth(14 * 4 / 3.);
    plus.setPreserveRatio(true);
    plus.setPickOnBounds(true);
    plus.setOnMouseClicked(onclick);
    return plus;
  }

  public void accept(TaskTagsAssignedEvent assigned) {
    final List<Node> newTags = new ArrayList<>();
    task.tags().forEach(tag -> {
      final ImageView cross = new ImageView(new javafx.scene.image.Image("/images/cross.png"));
      final StackPane tagItem = new StackPane(new Text((newTags.size() > 0 ? ", " : " ") + tag.name() + " "), new AnchorPane(cross));
      cross.setFitWidth(10);
      cross.setPreserveRatio(true);
      cross.setPickOnBounds(true);
      cross.setOnMouseClicked((evt) -> {
        task.untag(tag);
      });
      AnchorPane.setRightAnchor(cross, -3.0);
      AnchorPane.setTopAnchor(cross, 0.0);
      newTags.add(tagItem);
    });
    Platform.runLater(() -> {
      final ObservableList<Node> children = tags.getChildren();
      final Node plus = children.get(children.size() - 1);
      children.clear();
      children.addAll(newTags);
      children.add(plus);
    });
  }

  public void accept(TaskCallEvent call) {
    Platform.runLater(() -> {
      final ObservableList<Node> children = calls.getChildren();
      children.add(children.size() - 1, new Text((children.size() > 1 ? ", " : "") + call.phone()));
    });
  }

  public void accept(TaskInviteCanceledEvent cancel) {
    Platform.runLater(() -> {
      taskView.getChildren().clear();
      final ObservableList<Node> children = taskViewParent.getChildren();
      if (children.size() > 1)
        children.remove(1);
    });
  }

  public void accept(TaskInviteEvent invite) {
    final HBox box = new HBox();
    VBox.setVgrow(box, Priority.NEVER);
    box.setPadding(new Insets(10,0,0,0));
    final Button decline = new Button("Отказаться");
    final Button accept = new Button("Выполнить");
    final Region placeHolder = new Region();
    box.getChildren().addAll(
        decline,
        placeHolder,
        accept
    );
    HBox.setHgrow(placeHolder, Priority.ALWAYS);
    accept.setDefaultButton(true);
    accept.setOnAction(action -> {
      task.accept();
      taskViewParent.getChildren().remove(box);
    });
    decline.setOnAction(action -> {
      task.cancel();
      taskView.getChildren().clear();
      taskViewParent.getChildren().remove(box);
    });
    Platform.runLater(() -> taskViewParent.getChildren().add(box));
  }

  public void accept(TaskSuspendedEvent taskEvt) {
    this.task = null;
    controllers.clear();
    Platform.runLater(() -> {
      messagesView.getChildren().clear();
      taskViewParent.getChildren().clear();
      taskViewParent.getChildren().add(taskView);
      taskView.getChildren().clear();
      input.setEditable(false);
    });
  }

  public void accept(ChatMessageEvent event) {
    final Message source = event.source();
    if (source.from().resource().isEmpty() || // system messages
        event.task().offer().room().local().equals(source.from().resource()) ||
        source.has(Message.Subject.class) // topic started
        ) { // system messages
      return;
    }
    final MessageType type = task.owner().local().equals(source.from().resource()) ? MessageType.OUTGOING : MessageType.INCOMING;
    final CompositeMessageViewController finalVc = locateVCOfType(type, source.from(), task);
    if (source.has(Answer.class)) {
      finalVc.addText("Получен ответ от " + source.from().resource());
      finalVc.addAction("Перейти к ответу", () -> MainController.instance().selectEditor(source.id(), false, true));
    }
    else {
      event.visitParts(new ChatMessageEvent.PartsVisitor() {
        @Override
        public void accept(String text) {
          final String trim = text.trim();
          if (!trim.isEmpty())
            finalVc.addText(trim);
        }
        @Override
        public void accept(Image image) {
            finalVc.addImage(image);
        }
      });
      if (type == MessageType.INCOMING) { // incoming message from client
        input.setEditable(true);
      }
    }
  }

  private static RuntimeUtils.InvokeDispatcher dispatcher = new RuntimeUtils.InvokeDispatcher(DialogueController.class,
      obj -> log.warning("Unhandled event " + obj), "accept");
  @Override
  public void invoke(ExpertEvent expertEvent) {
    dispatcher.invoke(this, expertEvent);
  }

  public enum MessageType {
    INCOMING("/forms/chat/incoming.fxml", TextAlignment.LEFT),
    OUTGOING("/forms/chat/outgoing.fxml", TextAlignment.LEFT),
    TASK("/forms/task.fxml", TextAlignment.CENTER);

    private final URL fxml;
    private final TextAlignment alignment;

    MessageType(String fxml, TextAlignment alignment) {
      this.alignment = alignment;
      this.fxml = getClass().getResource(fxml);
      if (this.fxml == null)
        log.severe("Unable to load fxml for message type " + name());
    }

    public URL fxml() {
      return fxml;
    }

    public TextAlignment alignment() {
      return alignment;
    }

    public CompositeMessageViewController newInstance(VBox root, JID from, javafx.scene.image.Image ava) {
      return new CompositeMessageViewController(root, this, from, ava);
    }

    public String cssClass() {
      return name().toLowerCase();
    }
  }
}
