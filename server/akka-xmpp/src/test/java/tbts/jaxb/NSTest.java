package tbts.jaxb;

import com.tbts.xmpp.Features;
import com.tbts.xmpp.control.Bind;
import org.junit.Assert;
import org.junit.Test;

/**
 * User: solar
 * Date: 28.12.15
 * Time: 19:41
 */
public class NSTest {

  @Test
  public void testBosh() {
    Assert.assertEquals(
        "<stream:features xmlns:stream=\"http://etherx.jabber.org/streams\"><bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/></stream:features>",
        new Features(new Bind()).xmlString(true));
  }
}
