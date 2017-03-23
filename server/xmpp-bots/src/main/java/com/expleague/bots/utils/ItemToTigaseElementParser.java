package com.expleague.bots.utils;

import com.expleague.xmpp.Item;
import com.spbsu.commons.util.sync.StateLatch;
import tigase.jaxmpp.core.client.xml.ElementFactory;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.j2se.connectors.socket.StreamListener;
import tigase.jaxmpp.j2se.connectors.socket.XMPPDomBuilderHandler;
import tigase.jaxmpp.j2se.xml.J2seElement;
import tigase.xml.Element;
import tigase.xml.SimpleParser;

import java.util.Map;

/**
 * User: Artem
 * Date: 22.03.2017
 * Time: 16:01
 */
public class ItemToTigaseElementParser {

  public static tigase.jaxmpp.core.client.xml.Element parse(Item item) throws XMLException {
    final StateLatch latch = new StateLatch();
    final Element[] result = new Element[1];
    final XMPPDomBuilderHandler domHandler = new XMPPDomBuilderHandler(new StreamListener() {
      @Override
      public void nextElement(Element element) {
        System.out.println(element);
        result[0] = element;
        latch.advance();
      }

      @Override
      public void xmppStreamClosed() {
      }

      @Override
      public void xmppStreamOpened(Map<String, String> attribs) {
      }
    });

    final String xmlString = item.xmlString();
    final SimpleParser simpleParser = new SimpleParser();
    simpleParser.parse(domHandler, xmlString.toCharArray(), 0, xmlString.length());
    latch.state(2, 1);

    final tigase.jaxmpp.core.client.xml.Element element = ElementFactory.create(new J2seElement(result[0]));
    element.removeAttribute("xmlns"); //tigase assumes that XMLNS is stored in special field
    return element;
  }
}
