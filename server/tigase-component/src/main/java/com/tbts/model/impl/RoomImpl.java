package com.tbts.model.impl;

import com.amazonaws.util.StringInputStream;
import com.spbsu.commons.xml.JDOMUtil;
import com.tbts.model.*;
import com.tbts.model.handlers.Archive;
import com.tbts.model.handlers.ExpertManager;
import com.tbts.model.handlers.Reception;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jetbrains.annotations.Nullable;

import java.io.UnsupportedEncodingException;
import java.util.Set;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:25
 */
public class RoomImpl extends StateWise.Stub<Room.State, Room> implements Room {
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final String id;

  protected Expert worker;
  private Client owner;

  public RoomImpl(String id, Client client) {
    this.id = id;
    state = State.INIT;
    owner = client;
    addListener(Reception.instance());
  }

  public void fix() {
    if (state != State.COMPLETE)
      throw new IllegalStateException();
  }

  public void commit() {
    if (state != State.CLEAN && state != State.COMPLETE)
      throw new IllegalStateException();
    state(State.DEPLOYED);
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

  int postponedAnswers = 0;
  @Override
  public void answer() {
    if (state() != State.LOCKED) {
      if (state() == State.DEPLOYED)
        postponedAnswers++;
      else
        throw new IllegalStateException();
    }
    state(State.COMPLETE);
    worker.free();
    owner.feedback(this);
  }

  @Override
  public void cancel() {
    state(State.CANCELED);
    if (worker != null)
      worker.free();
  }

  @Override
  public void enter(Expert winner) {
    if (winner == null)
      throw new IllegalStateException();
    worker = winner;
    state(State.LOCKED);
    if (postponedAnswers > 0) {
      postponedAnswers = 0;
      answer();
    }
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
  public boolean relevant(Expert expert) {
    return true;
  }

  @Override
  public long invitationTimeout() {
    return ExpertManager.EXPERT_ACCEPT_INVITATION_TIMEOUT;
  }

  @Override
  public void exit() {
    if (state() == State.LOCKED)
      state(State.DEPLOYED);
  }

  @Override
  public Client owner() {
    return owner;
  }

  @Nullable
  @Override
  public Expert worker() {
    return worker;
  }

  @Override
  public void onMessage(String author, CharSequence text) {
    Archive.instance().log(this, author, text);
  }
}
