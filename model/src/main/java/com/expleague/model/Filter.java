package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 04/03/16.
 */
@XmlRootElement(name = "experts-filter")
public class Filter extends Attachment {

  @XmlElement(namespace = Operations.NS)
  private List<JID> reject;

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "unused"})
  @XmlElement(namespace = Operations.NS)
  private List<JID> accept;

  @XmlElement(namespace = Operations.NS)
  private List<JID> prefer;

  public Filter() {
  }

  public Filter(List<JID> accept, List<JID> reject, List<JID> prefer) {
    this.accept = accept;
    this.reject = reject;
    this.prefer = prefer;
  }

  public boolean fit(JID who) {
    if (accept != null)
      return accept.contains(who);
    else if (reject != null)
      return !reject.contains(who);
    return true;
  }

  public void reject(JID slacker) {
    if (reject == null)
      reject = new ArrayList<>();
    if (accept != null) {
      accept.remove(slacker);
    }
    if (prefer != null) {
      prefer.remove(slacker);
    }
    if (!reject.contains(slacker))
      reject.add(slacker);
  }

  public void prefer(JID... worker) {
    if (prefer == null)
      prefer = new ArrayList<>();
    for (int i = 0; i < worker.length; i++) {
      if (prefer.contains(worker[i]) || accept.contains(worker[i]))
        continue;
      prefer.add(worker[i]);
    }
  }

  public boolean isPrefered(JID jid) {
    if (accept != null)
      return accept.contains(jid);
    return prefer != null && prefer.contains(jid) && (reject == null || !reject.contains(jid));
  }

  public Stream<JID> rejected() {
    return reject != null ? reject.stream() : Stream.empty();
  }

  public Stream<JID> accepted() {
    return accept != null ? accept.stream() : Stream.empty();
  }

  public Stream<JID> preferred() {
    return prefer != null ? prefer.stream() : Stream.empty();
  }
}
