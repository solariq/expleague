package com.tbts.xmpp.stanza;

import com.spbsu.commons.util.Holder;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import java.nio.ByteBuffer;
import java.util.Base64;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 23:46
 */
public class Stanza extends Item {
  @XmlAttribute
  protected JID from;
  @XmlAttribute
  protected JID to;
  @XmlAttribute
  protected String id;
  @XmlAttribute
  protected StanzaType type;


  public Stanza() {
    id = generateId();
  }

  private static final ThreadLocal<Holder<Long>> thId = new ThreadLocal<Holder<Long>>() {
    @Override
    protected Holder<Long> initialValue() {
      return new Holder<>(((long)Thread.currentThread().hashCode() + System.currentTimeMillis()) << 32);
    }
  };

  public Stanza(String id, StanzaType type) {
    this.id = id;
    this.type = type;
  }

  private static String generateId() {
    final Holder<Long> holder = thId.get();
    final Long value = holder.getValue();
    holder.setValue(value + 1);
    final ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.asLongBuffer().put(value);
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  public JID from() {
    return from;
  }
  public void from(JID jid) {
    this.from = jid;
  }

  public JID to() {
    return to;
  }
  public void to(JID to) {
    this.to = to;
  }


  public StanzaType type() {
    return type;
  }
  public void type(StanzaType type) {
    this.type = type;
  }

  public String id() {
    return id;
  }

  @XmlEnum
  public enum StanzaType {
    @XmlEnumValue(value = "error") ERROR,
    @XmlEnumValue(value = "get") GET,
    @XmlEnumValue(value = "set") SET,
    @XmlEnumValue(value = "result") RESULT,
    @XmlEnumValue(value = "groupchat") GROUP_CHAT,
  }
}
