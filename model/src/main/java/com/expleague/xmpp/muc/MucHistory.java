package com.expleague.xmpp.muc;

import com.expleague.xmpp.stanza.Stanza;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 28.01.17.
 */
@XmlRootElement(name = "history", namespace = MucXData.MUC_NS)
public class MucHistory extends com.expleague.xmpp.Item {
  @XmlAttribute
  private int maxstanzas = -1;

  public MucHistory() {
  }

  public Stream<Stanza> filter(List<Stanza> archive) {
    if (maxstanzas >= 0)
      return archive.subList(archive.size() - maxstanzas, archive.size()).stream();
    return archive.stream();
  }
}
