package com.expleague.xmpp;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.aalto.stax.OutputFactoryImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.spbsu.commons.io.StreamTools;
import com.expleague.util.xml.AsyncJAXBStreamReader;
import com.expleague.util.xml.BOSHNamespaceContext;
import com.expleague.util.xml.LazyNSXMLStreamWriter;
import com.expleague.util.xml.XMPPStreamNamespaceContext;
import org.codehaus.stax2.XMLOutputFactory2;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 08.12.15
 * Time: 17:57
 */
@XmlTransient
public class Item implements Cloneable {
  private static final Logger log = Logger.getLogger(Item.class.getName());
  public static final String XMPP_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\" xmlns=\"jabber:client\" xml:lang=\"en\" xmlns:xml=\"http://www.w3.org/XML/1998/namespace\">";
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

  protected static ThreadLocal<ObjectMapper> tlObjectMapper = new ThreadLocal<ObjectMapper>() {
    @Override
    protected ObjectMapper initialValue() {
      final ObjectMapper mapper = new ObjectMapper();
      final AnnotationIntrospector introspector = new JaxbAnnotationIntrospector(TypeFactory.defaultInstance());
      mapper.getDeserializationConfig().with(introspector);
      mapper.getSerializationConfig().with(introspector);
      return mapper;
    }
  };

  public static <T extends Item> T create(CharSequence str) {
    final XmlInputter inputter = tlReader.get();
    try {
      return (T)inputter.deserialize(str.toString());
    }
    catch (Exception e) {
      inputter.init();
      try {
        return (T) inputter.deserialize(str.toString());
      }
      catch (Exception ee) {
        log.log(Level.WARNING, "Unable to parse message: " + str.toString(), ee);
        return null;
      }
    }
  }

  @Nullable
  public static <T extends Item> T createJson(CharSequence str, Class<T> clazz) {
    try {
      return tlObjectMapper.get().readValue(str.toString(), clazz);
    }
    catch (IOException e) {
      log.log(Level.WARNING, "Unable to read item of type " + clazz.getName() + " from JSON", e);
    }
    return null;
  }

  @Nullable
  public String jsonString() {
    final ObjectMapper mapper = tlObjectMapper.get();
    try {
      return mapper.writeValueAsString(this);
    }
    catch (JsonProcessingException e) {
      log.log(Level.WARNING, "Unable to convert item to JSON", e);
      return null;
    }
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
    private static final int MAXIMUM_BUFFER_SIZE = 1 << 20;
    private AsyncXMLStreamReader<AsyncByteArrayFeeder> asyncXml;
    private AsyncJAXBStreamReader reader;

    public XmlInputter() {
      init();
    }

    void init() {
      final AsyncXMLInputFactory factory = new InputFactoryImpl();
      asyncXml = factory.createAsyncForByteArray();
      reader = new AsyncJAXBStreamReader(asyncXml, Stream.jaxb());
      try {
        asyncXml.getInputFeeder().feedInput(XMPP_START.getBytes(), 0, XMPP_START.length());
        reader.drain(o -> { });
      }
      catch (XMLStreamException | SAXException e) {
        throw new RuntimeException(e);
      }
    }
    Item result;
    int deserializedLength;

    public Item deserialize(String xmlForm) {
      return deserialize(xmlForm.getBytes(StreamTools.UTF));
    }

    public Item deserialize(byte[] xmlForm) {
      try {
        deserializedLength += xmlForm.length;
        if (deserializedLength > MAXIMUM_BUFFER_SIZE)
          init();
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
}
