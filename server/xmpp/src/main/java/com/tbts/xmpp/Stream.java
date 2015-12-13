package com.tbts.xmpp;

import com.spbsu.commons.system.RuntimeUtils;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:17
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Stream {
  @XmlAttribute
  private String version;
  @XmlAttribute(namespace = "http://www.w3.org/XML/1998/namespace")
  private String lang;
  @XmlAnyElement(lax = true)
  private List<? extends Item> contents = new ArrayList<>();

  public List<? extends Item> contents() {
    return contents;
  }

  public String version() {
    return version;
  }

  public String lang() {
    return lang;
  }

  private static final JAXBContext context;
  static {
    try {
      final List<Class> classesInPackage = Arrays.asList(RuntimeUtils.packageResourcesList(Stream.class.getPackage().getName())).stream().filter(p -> p.endsWith(".class")).map(resource -> {
        try {
          final String name = resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
          return Class.forName(name);
        }
        catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }).filter(c -> !c.isInterface()).collect(Collectors.toList());
      context = JAXBContext.newInstance(classesInPackage.toArray(new Class[classesInPackage.size()]));
    }
    catch (JAXBException | IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  public static JAXBContext jaxb() {
    return context;
  }

  public static Marshaller marshaller() {
    final Marshaller marshaller;
    try {
      marshaller = jaxb().createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
      marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
        @Override
        public String[] getPreDeclaredNamespaceUris() {
          return new String[]{"http://etherx.jabber.org/streams", "http://www.w3.org/XML/1998/namespace", "jabber:client"};
        }

        @Override
        public String getPreferredPrefix(String ns, String suggest, boolean requirePrefix) {
//          System.out.println(ns + " " + suggest + " " + requirePrefix);
          switch (ns) {
            case "http://etherx.jabber.org/streams":
              return "stream";
            case "http://www.w3.org/XML/1998/namespace":
              return "xml";
            default:
              return "";
          }
        }
      });
    }
    catch (JAXBException e) {
      throw new RuntimeException(e);
    }
    return marshaller;
  }
}
