package com.expleague.xmpp.control.expleague;

import com.expleague.model.Operations;
import com.expleague.model.Tag;
import com.expleague.server.services.TagsService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "query", namespace = TagsQuery.TAGS_SCHEME)
public class TagsQuery extends Item {
  public static final String TAGS_SCHEME = "http://expleague.com/scheme/tags";
  static {
    XMPPServices.register(TAGS_SCHEME, TagsService.class, "tags");
  }
  @XmlElement(name = "tag", namespace = Operations.NS)
  private List<Tag> tags;

  public TagsQuery(List<Tag> tags) {
    this.tags = tags;
  }

  public TagsQuery() {
  }

  public List<Tag> tags() {
    return tags;
  }
}
