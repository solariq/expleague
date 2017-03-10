package com.expleague.bots.utils;

import com.spbsu.commons.util.Pair;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.Element;
import tigase.jaxmpp.core.client.xmpp.stanzas.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Artem
 * Date: 10.03.2017
 * Time: 16:17
 */
public class ExpectedMessage {
  private final Map<Pair<String[], String>, List<Pair<String, String>>> childrenAndAttributes;
  private boolean received = false;

  public ExpectedMessage(Map<Pair<String[], String>, List<Pair<String, String>>> childrenAndAttributes) {
    this.childrenAndAttributes = childrenAndAttributes;
  }

  public boolean received() {
    return received;
  }

  public boolean tryReceive(Message message) throws JaxmppException {
    for (Map.Entry<Pair<String[], String>, List<Pair<String, String>>> entry : childrenAndAttributes.entrySet()) {
      final Element element = message.findChild(entry.getKey().first);
      if (element == null) {
        return false;
      }
      if (element.getValue() != null && !element.getValue().equals(entry.getKey().second)) {
        return false;
      }

      if (entry.getValue() != null) {
        for (Pair<String, String> attribute : entry.getValue()) {
          if (!attribute.second.equals(element.getAttribute(attribute.first))) {
            return false;
          }
        }
      }
    }
    received = true;
    return true;
  }

  public static ExpectedMessage create(String childName, String childBody, List<Pair<String, String>> attributes) {
    return create(new String[]{"message", childName}, childBody, attributes);
  }

  public static ExpectedMessage create(String[] childPath, String childBody, List<Pair<String, String>> attributes) {
    final Map<Pair<String[], String>, List<Pair<String, String>>> childrenAndAttributes = new HashMap<Pair<String[], String>, List<Pair<String, String>>>() {{
      put(new Pair<>(childPath, childBody), attributes);
    }};
    return new ExpectedMessage(childrenAndAttributes);
  }
}
