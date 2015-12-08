package com.tbts.util.xml;

import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.spbsu.commons.func.Action;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 15:49
 */
public class AsyncJAXBStreamReader {
  private final JAXBObjectBuilder builder;

  public AsyncJAXBStreamReader(String name, String ns, Class<?> jaxbSchema) {
    //noinspection unchecked
    builder = new JAXBObjectBuilder(name, ns, jaxbSchema);
  }

  public void drain(AsyncXMLStreamReader xmlReader, Action action) throws XMLStreamException {
    int next;
    while ((next = xmlReader.next()) != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
      printStateName(next);
      switch (next) {
        case XMLEvent.START_DOCUMENT: break;
        case XMLEvent.END_DOCUMENT: return;
        case XMLEvent.START_ELEMENT: // fixing lack of attribute event
          builder.acceptFlat(next, xmlReader, action);
          next = XMLEvent.ATTRIBUTE;
        default:
          builder.acceptFlat(next, xmlReader, action);
      }
    }
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
