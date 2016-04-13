package com.expleague.xmpp.control.expleague;

import com.expleague.model.Operations;
import com.expleague.model.Pattern;
import com.expleague.server.services.PatternsService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.*;
import java.util.List;

/**
 * Experts League
 * Created by solar on 28/03/16.
 */
@SuppressWarnings("unused")
@XmlRootElement(name = "query", namespace = PatternsQuery.PATTERNS_SCHEME)
public class PatternsQuery extends Item {
  public static final String PATTERNS_SCHEME = "http://expleague.com/scheme/patterns";
  static {
    XMPPServices.register(PATTERNS_SCHEME, PatternsService.class, "patterns");
  }
  @XmlAttribute
  private Intent intent;

  @XmlElement(name = "pattern", namespace = Operations.NS)
  private List<Pattern> patterns;

  public PatternsQuery(List<Pattern> patterns) {
    this.patterns = patterns;
  }

  public PatternsQuery(Intent intent) {
    this.intent = intent;
  }

  public PatternsQuery() {
  }

  public Intent intent() {
    return intent != null ? intent : Intent.PRESENTATION;
  }

  public List<Pattern> patterns() {
    return patterns;
  }
}
