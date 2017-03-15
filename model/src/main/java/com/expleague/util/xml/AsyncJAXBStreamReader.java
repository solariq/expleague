package com.expleague.util.xml;

import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.spbsu.commons.func.Action;
import com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl;
import com.expleague.util.xml.stolen.StAXStreamConnector;
import com.expleague.xmpp.Stream;
import com.sun.xml.bind.v2.runtime.unmarshaller.XmlVisitor;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 15:49
 */
public class AsyncJAXBStreamReader {
  private static final Logger log = Logger.getLogger(AsyncJAXBStreamReader.class.getName());
  private final StAXStreamConnector connector;
  private Action action;

  public AsyncJAXBStreamReader(AsyncXMLStreamReader reader, JAXBContext context) {
    try {
      final UnmarshallerImpl unmarshaller = (UnmarshallerImpl)context.createUnmarshaller();
      unmarshaller.setListener(new Unmarshaller.Listener() {
        @Override
        public void afterUnmarshal(Object target, Object parent) {
          if (parent instanceof Stream)
            //noinspection unchecked
            action.invoke(target);
        }
      });
      final XmlVisitor handler = unmarshaller.createUnmarshallerHandler(null, false, null);
      handler.getContext().clearStates();
      connector = new StAXStreamConnector(reader, handler);
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    }
    //noinspection unchecked
  }

  public void drain(Action action) throws XMLStreamException, SAXException {
    this.action = action;
    connector.drain();
  }

  private void printStateName(int next) {
    final Field[] fields = XMLStreamConstants.class.getDeclaredFields();
    for (final Field field : fields) {
      try {
        if (int.class.equals(field.getType()) && (field.getModifiers() & Modifier.STATIC) != 0) {
          if (next == field.getInt(null))
            log.finest(field.getName());
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
