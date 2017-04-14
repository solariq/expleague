package com.expleague.xmpp.stanza;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.spbsu.commons.random.FastRandom;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.XmlAttribute;

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

  public static Stanza create(final CharSequence str, final long timestampSec) {
    final Stanza stanza = Item.create(str);
    if (!stanza.isTimestampPresent()) {
      stanza.id = stanza.id + "-" + timestampSec;
    }
    return stanza;
  }

  private static final ThreadLocal<FastRandom> thRandom = new ThreadLocal<FastRandom>() {
    @Override
    protected FastRandom initialValue() {
      return new FastRandom(Thread.currentThread().hashCode());
    }
  };

  public Stanza(String id) {
    this.id = id;
  }

  public static String generateId() {
    return thRandom.get().nextBase64String(10) + "-" + (System.currentTimeMillis() / 1000);
  }

  public JID from() {
    return from;
  }

  public <S extends Stanza> S from(JID jid) {
    this.from = jid;
    //noinspection unchecked
    return (S) this;
  }

  @Override
  public <T extends Item> T copy(){
    return copy(null);
  }

  public <T extends Item> T copy(@Nullable String idSuffix){
    final Stanza clone = super.copy();

    if (idSuffix == null)
      clone.id = generateId();
    else if (idSuffix.isEmpty())
      clone.id = id;
    else
      clone.id = id + "-" + idSuffix;

    //noinspection unchecked
    return (T)clone;
  }

  public JID to() {
    return to;
  }

  public <S extends Stanza> S to(JID to) {
    this.to = to;
    //noinspection unchecked
    return (S) this;
  }

  public boolean isBroadcast() {
    return to == null;
  }

  private boolean isTimestampPresent() {
    final int separator = id.lastIndexOf('-');
    if (separator != -1) {
      try {
        //noinspection ResultOfMethodCallIgnored
        Long.parseLong(id.substring(separator + 1));
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  public long ts() {
    if (isTimestampPresent()) {
      return Long.parseLong(id.substring(id.lastIndexOf('-') + 1)) * 1000;
    }
    else {
      return System.currentTimeMillis();
    }
  }

  public String id() {
    return id;
  }

  public String strippedVitalikId() {
    final int beginIndex = id.lastIndexOf('-');
    if (beginIndex > 0)
      return id.substring(0, beginIndex);
    return id;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Stanza && ((Stanza)obj).id.equals(id);
  }
}
