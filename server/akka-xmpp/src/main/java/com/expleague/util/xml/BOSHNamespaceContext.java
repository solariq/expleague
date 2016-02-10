package com.expleague.util.xml;

import javax.xml.namespace.NamespaceContext;
import java.util.Collections;
import java.util.Iterator;

/**
 * User: solar
 * Date: 13.12.15
 * Time: 13:18
 */
public class BOSHNamespaceContext implements NamespaceContext {
  @Override
  public String getNamespaceURI(String prefix) {
    switch (prefix) {
      case "xml":
        return "http://www.w3.org/XML/1998/namespace";
    }
    return null;
  }

  @Override
  public String getPrefix(String namespaceURI) {
    switch (namespaceURI) {
      case "http://etherx.jabber.org/streams":
        return "stream";
      case "http://www.w3.org/XML/1998/namespace":
        return "xml";
    }
    return "";
  }

  @Override
  public Iterator getPrefixes(String namespaceURI) {
    return Collections.singletonList(getPrefix(namespaceURI)).iterator();
  }
}
