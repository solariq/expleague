package com.tbts.model.impl;

import com.amazonaws.util.StringInputStream;
import com.spbsu.commons.func.Action;
import com.spbsu.commons.func.impl.WeakListenerHolderImpl;
import com.spbsu.commons.xml.JDOMUtil;
import com.tbts.model.*;
import com.tbts.model.handlers.Archive;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:25
 */
public class RoomImpl extends WeakListenerHolderImpl<Room> implements Room {
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final Action<Client> clientLst;
  private final Set<Expert> invited = new HashSet<>();
  private final String id;
  @SuppressWarnings("FieldCanBeLocal")
  private final Action<Expert> clearInviteLst;
  private final Client owner;

  protected State state;
  protected Action<Expert> workerListener;

  public RoomImpl(String id, Client client) {
    this.owner = client;
    this.id = id;
    state = State.INIT;
    client.addListener(clientLst = cl -> {
      if (cl.active() != this)
        return;
      switch (cl.state()) {
        case CHAT:
        case COMMITED:
          commit();
          break;
        case FEEDBACK:
          fix();
          break;
      }
    });
    workerListener = expert -> {
      expert.removeListener(workerListener);
      if (expert.state() != Expert.State.GO && answersCountOnDeploy == answers.size()) {
        answer(Answer.EMPTY);
      }
    };

    clearInviteLst = expert -> {
      if (expert.state() == Expert.State.AWAY) {
        invited.remove(expert);
      }
    };
    ExpertManager.instance().addListener(clearInviteLst);
    addListener(Reception.instance());
  }

  protected void fix() {
    if (state != State.COMPLETE)
      throw new IllegalStateException();
  }

  protected void commit() {
    if (state != State.CLEAN && state != State.COMPLETE)
      throw new IllegalStateException();
    answersCountOnDeploy = answers.size();
    state(State.DEPLOYED);
    ExpertManager.instance().challenge(this);
  }

  @Override
  public String id() {
    return id;
  }

  public Query query() {
    final Query.Builder builder = new Query.Builder();
    Archive.instance().visitMessages(this, (authorId, message, ts) -> {
      try {
        final Element element = JDOMUtil.loadXML(new StringInputStream(message.toString()));
        final Element subject = element.getChild("subject", Namespace.getNamespace("jabber:client"));
        if (subject != null)
          builder.addText(subject.getText());
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      return true;
    });
    return builder.build();
  }

  protected void state(State state) {
    this.state = state;
    invoke(this);
  }

  private final List<Answer> answers = new ArrayList<>();
  private int answersCountOnDeploy;
  @Override
  public void answer(Answer answer) {
    answers.add(answer);
    if (state() == State.LOCKED)
      state(State.COMPLETE);
  }

  @Override
  public void enter(Expert winner) {
    invited.remove(winner);
    winner.addListener(workerListener);
    state(State.LOCKED);
  }

  @Override
  public void open() {
    state(State.CLEAN);
  }

  @Override
  public boolean quorum(Set<Expert> reserved) {
    return reserved.size() > 0;
  }

  @Override
  public void invite(Expert next) {
    if (next.active() != this)
      throw new IllegalArgumentException();
    invited.add(next);
    next.invite();
  }

  @Override
  public boolean relevant(Expert expert) {
    return !invited.contains(expert);
  }

  @Override
  public long invitationTimeout() {
    return ExpertManager.EXPERT_ACCEPT_INVITATION_TIMEOUT;
  }

  @Override
  public void exit(Expert expert) {
  }

  @Override
  public List<Answer> answers() {
    return answers;
  }

  @Override
  public void onMessage(String author, CharSequence text) {
    Archive.instance().log(this, author, text);
  }

  @Override
  public State state() {
    return state;
  }
}
