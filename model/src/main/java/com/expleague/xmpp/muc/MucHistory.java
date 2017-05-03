package com.expleague.xmpp.muc;

import com.expleague.xmpp.stanza.Stanza;
import com.sun.org.apache.xpath.internal.operations.Bool;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28.01.17.
 */
@XmlRootElement(name = "history", namespace = MucXData.MUC_NS)
public class MucHistory extends com.expleague.xmpp.Item {
  @XmlAttribute
  private Integer maxstanzas;

  @XmlAttribute(name = "last-id")
  private String lastId;

  @XmlAttribute(name = "recent")
  private Boolean recent;

  public MucHistory() {
  }

  // TODO: exclude internal messages from the archive
  public Stream<Stanza> filter(List<Stanza> archive) {
    if (maxstanzas != null)
      return archive.subList(archive.size() - maxstanzas, archive.size()).stream();
    else if (lastId != null) {
      for (int i = 0; i < archive.size(); i++) {
        if (lastId.startsWith(archive.get(i).id()))
          return archive.subList(i + 1, archive.size()).stream();
      }
    }
    return archive.stream();
  }

  public void recent(boolean recent) {
    this.recent = recent;
  }

  public boolean recent() {
    return recent != null && recent;
  }
}
