package com.tbts.xmpp.stanza;

import com.spbsu.commons.util.Holder;
import com.tbts.xmpp.Item;
import com.tbts.xmpp.JID;

import javax.xml.bind.annotation.XmlAttribute;
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

  public Stanza() {
    id = generateId();
  }

  private static final ThreadLocal<Holder<Long>> thId = new ThreadLocal<Holder<Long>>() {
    @Override
    protected Holder<Long> initialValue() {
      return new Holder<>(((long)Thread.currentThread().hashCode() + System.currentTimeMillis()) << 32);
    }
  };

  public Stanza(String id) {
    this.id = id;
  }

  private static String generateId() {
    final Holder<Long> holder = thId.get();
    final Long value = holder.getValue();
    holder.setValue(value + 1);
    final ByteBuffer buffer = ByteBuffer.allocate(8);
    buffer.asLongBuffer().put(value);
    return Base64.getEncoder().encodeToString(buffer.array());
  }
}
