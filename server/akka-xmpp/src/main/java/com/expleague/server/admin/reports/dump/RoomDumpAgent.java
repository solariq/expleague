package com.expleague.server.admin.reports.dump;

import akka.actor.Props;
import com.expleague.server.dao.Archive;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.PersistentActorAdapter;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: Artem
 * Date: 25.08.2017
 */
public class RoomDumpAgent extends PersistentActorAdapter {
  private final JID jid;
  private final List<Message> archive = new ArrayList<>();

  public RoomDumpAgent(JID jid) {
    this.jid = jid;
  }

  @ActorMethod
  public void onDumpRequest(DumpRequest dumpRequest) {
    final String lastMessage = Archive.instance().lastMessageId(jid.local());
    if (lastMessage != null && archive.size() > 0 && archive.get(archive.size() - 1).id().equals(lastMessage)) {
      sender().tell(archive, self());
    }
    else {
      sender().tell(Archive.instance().dump(jid.local())
              .stream().filter(stanza -> stanza instanceof Message)
              .map(stanza -> (Message) stanza).collect(Collectors.toList()),
          self());
    }
  }

  @Override
  public void onReceiveRecover(Object o) throws Exception {
    if (o instanceof Message) {
      archive.add((Message) o);
    }
  }

  @Override
  public String persistenceId() {
    return jid.local();
  }

  public static Props props(JID jid) {
    return props(RoomDumpAgent.class, jid);
  }

  public static class DumpRequest {
  }
}
