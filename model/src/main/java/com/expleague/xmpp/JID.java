package com.expleague.xmpp;

import com.sun.istack.Interned;

import javax.xml.bind.annotation.XmlValue;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * User: solar
 * Date: 07.12.15
 * Time: 13:01
 */
public class JID implements Serializable, Cloneable {
  @Interned
  private String bare;
  private String resource;

  @SuppressWarnings("unused")
  public JID() {}

  private JID(String bare, String resource) {
    this.bare = bare;
    this.resource = "".equals(resource) ? null : resource;
  }

  public JID(String local, String domain, String resource) {
    this.bare = (local + "@" + domain).toLowerCase().intern();
    this.resource = resource != null ? resource.toLowerCase() : null;
  }

  public URI asURI() {
    try {
      return new URI(bare);
    }
    catch (URISyntaxException ignore) {
      throw new RuntimeException(ignore);
    }
  }

  public JID bare() {
    return resource != null ? new JID(bare, null) : this;
  }

  @XmlValue
  public String getAddr() {
    return resource == null ? bare : bare + "/" + resource;
  }

  @SuppressWarnings("unused") // needed for unmarshaling inside JAXB
  public void setAddr(String addr) {
    addr = addr.toLowerCase();
    //noinspection ResultOfMethodCallIgnored
    URI.create(addr); // check syntax
    final int resourceStart = addr.indexOf('/');
    bare = resourceStart >= 0 ? addr.substring(0, resourceStart).intern() : addr.intern();
    resource = resourceStart >= 0 ? addr.substring(resourceStart + 1) : null;
  }

  @Override
  public String toString() {
    return getAddr();
  }

  @Override
  public int hashCode() {
    int result = bare.hashCode();
    result = 31 * result + (resource != null ? resource.hashCode() : 0);
    return result;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof JID))
      return false;
    final JID jid = (JID) obj;
    //noinspection StringEquality
    return jid.bare == bare && (resource == jid.resource || (resource != null && resource.equals(jid.resource)));
  }

  public boolean hasResource() {
    return resource != null;
  }

  public static JID parse(String addr) {
    try {
      addr = addr.toLowerCase();
      //noinspection ResultOfMethodCallIgnored
      URI.create(addr); // check syntax
      final int resourceStart = addr.indexOf('/');
      final String bare = resourceStart >= 0 ? addr.substring(0, resourceStart).intern() : addr.intern();
      final String resource = resourceStart >= 0 ? addr.substring(resourceStart + 1) : null;
      return new JID(bare, resource);
    }
    catch (Exception e) {
      return null;
    }
  }

  public JID resource(String bind) {
    return new JID(bare, bind.toLowerCase());
  }

  public boolean bareEq(JID to) {
//    if (to != null && this.bare.equals(to.bare) && this.bare != to.bare)
//      System.out.println();
    //noinspection StringEquality
    return to != null && this.bare == to.bare;
  }

  public String local() {
    final int dogIndex = bare.indexOf('@');
    return dogIndex >= 0 ? bare.substring(0, dogIndex) : "";
  }

  public String domain() {
    final int dogIndex = bare.indexOf('@');
    return dogIndex >= 0 ? bare.substring(dogIndex + 1) : bare;
  }

  public String resource() {
    return resource != null ? resource : "";
  }


  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.writeUTF(bare);
    out.writeUTF(resource());
  }
  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    bare = in.readUTF().intern();
    final String resource = in.readUTF();
    this.resource = resource.isEmpty() ? null : resource;
  }
  private void readObjectNoData() throws ObjectStreamException {
  }
}
