package com.tbts.xmpp;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.aalto.stax.OutputFactoryImpl;
import com.spbsu.commons.io.StreamTools;
import com.tbts.server.xmpp.XMPPOutFlow;
import com.tbts.util.xml.AsyncJAXBStreamReader;
import com.tbts.util.xml.BOSHNamespaceContext;
import com.tbts.util.xml.LazyNSXMLStreamWriter;
import com.tbts.util.xml.XMPPStreamNamespaceContext;
import org.codehaus.stax2.XMLOutputFactory2;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:57
 */
@XmlTransient
public class Item implements Serializable, Cloneable {
  public static Item EMPTY = new Item();
  private static ThreadLocal<XmlOutputter> tlWriter = new ThreadLocal<XmlOutputter>() {
    @Override
    protected XmlOutputter initialValue() {
      return new XmlOutputter(false);
    }
  };
  private static ThreadLocal<XmlOutputter> tlWriterBosh = new ThreadLocal<XmlOutputter>() {
    @Override
    protected XmlOutputter initialValue() {
      return new XmlOutputter(true);
    }
  };
  private static ThreadLocal<XmlInputter> tlReader = new ThreadLocal<XmlInputter>() {
    @Override
    protected XmlInputter initialValue() {
      return new XmlInputter();
    }
  };

  public static Item create(CharSequence str) {
    return tlReader.get().deserialize(str.toString());
  }

  public String xmlString() {
    return tlWriter.get().serialize(this);
  }

  public String xmlString(boolean bosh) {
    return bosh ? tlWriterBosh.get() .serialize(this) : tlWriter.get().serialize(this);
  }

  @Override
  public String toString() {
    return xmlString();
  }


  private static Map<Class<? extends Item>, String> nsMap = new ConcurrentHashMap<>();
  public static <T extends Item> String ns(T item) {
    String cached = nsMap.get(item.getClass());
    if (cached == null) {
      final QName qName = Stream.jaxb().createJAXBIntrospector().getElementName(item);
      nsMap.put(item.getClass(), cached = qName.getNamespaceURI());
    }
    return cached;
  }

  public <T extends Item> T copy() {
    try {
      //noinspection unchecked
      return (T)clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  private static class XmlOutputter {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final XMLStreamWriter writer;

    public XmlOutputter(boolean bosh) {
      final OutputFactoryImpl factory = new OutputFactoryImpl();
      factory.configureForSpeed();
      factory.setProperty(XMLOutputFactory2.XSP_NAMESPACE_AWARE, true);
      factory.setProperty(XMLOutputFactory2.IS_REPAIRING_NAMESPACES, true);
      factory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, true);
//      factory.setProperty(XMLOutputFactory2.P_AUTOMATIC_NS_PREFIX, true);
      try {
        writer = new LazyNSXMLStreamWriter(factory.createXMLStreamWriter(output), bosh);
        writer.setNamespaceContext(bosh ? new BOSHNamespaceContext() : new XMPPStreamNamespaceContext());
        writer.writeStartDocument();
        if (bosh)
          writer.writeStartElement(BoshBody.NS, "body");
        else
          writer.writeStartElement(Stream.NS, "stream");
        writer.writeCharacters("");
        writer.flush();
      }
      catch (XMLStreamException e) {
        throw new RuntimeException(e);
      }
    }

    public String serialize(Item item) {
      output.reset();
      try {
        final Marshaller marshaller = Stream.jaxb().createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.marshal(item, writer);
        writer.flush();
      }
      catch (JAXBException | XMLStreamException e) {
        throw new RuntimeException(e);
      }

      return new String(output.toByteArray(), StreamTools.UTF);
    }
  }

  private static class XmlInputter {
    private final AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
    private final AsyncJAXBStreamReader reader;

    public XmlInputter() {
      final AsyncXMLInputFactory factory = new InputFactoryImpl();
      asyncXml = factory.createAsyncForByteArray();
      reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
      try {
        asyncXml.getInputFeeder().feedInput(XMPPOutFlow.XMPP_START.getBytes(), 0, XMPPOutFlow.XMPP_START.length());
        reader.drain(o -> { });
      }
      catch (XMLStreamException | SAXException e) {
        throw new RuntimeException(e);
      }
    }

    Item result;

    public Item deserialize(String xmlForm) {
      return deserialize(xmlForm.getBytes(StreamTools.UTF));
    }

    public Item deserialize(byte[] xmlForm) {
      try {
        asyncXml.getInputFeeder().feedInput(xmlForm, 0, xmlForm.length);
        reader.drain(o -> {
          if (o instanceof Item)
            result = (Item)o;
        });
      }
      catch (XMLStreamException | SAXException e) {
        throw new RuntimeException(e);
      }
      return result;
    }
  }

//  private void writeObject(ObjectOutputStream out) throws IOException {
//    out.writeUTF(xmlString());
//  }
//  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
//    final String xml = in.readUTF();
//
//  }
//  private void readObjectNoData() throws ObjectStreamException {
//  }
}
