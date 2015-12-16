package com.tbts.util.xml;

import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.spbsu.commons.func.Action;
import com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallerImpl;
import com.tbts.util.xml.stolen.StAXStreamConnector;
import com.tbts.xmpp.Stream;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 15:49
 */
public class AsyncJAXBStreamReader {
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
      connector = new StAXStreamConnector(reader, unmarshaller.createUnmarshallerHandler(null, false, null));
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
            System.out.println(field.getName());
        }
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
