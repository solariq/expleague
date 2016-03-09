package com.expleague.model;

import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Experts League
 * Created by solar on 04/03/16.
 */
@XmlRootElement(name = "experts-filter")
public class Filter {

  @XmlAnyElement(lax = true)
  private List<JID> reject;

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "unused"})
  @XmlAnyElement(lax = true)
  private List<JID> accept;

  @XmlAnyElement(lax = true)
  private List<JID> prefer;

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
    reject.add(slacker);
  }

  public void prefer(JID worker) {
    if (prefer == null)
      prefer = new ArrayList<>();
    prefer.add(worker);
  }

  public boolean isPrefered(JID jid) {
    if (accept != null)
      return accept.contains(jid);
    return prefer != null && prefer.contains(jid) && (reject == null || !reject.contains(jid));
  }
}
