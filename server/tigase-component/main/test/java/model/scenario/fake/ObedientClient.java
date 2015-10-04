package model.scenario.fake;

import com.tbts.model.Query;
import com.tbts.model.impl.ClientImpl;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:47
 */
public class ObedientClient extends ClientImpl {
  public ObedientClient() throws TigaseStringprepException {
    super(BareJID.bareJIDInstance("client@localhost"));
  }

  public void query(Query query) {
    allocated.query(query);
    query();
  }

  @Override
  public void feedback() {
    super.feedback();
    state(State.ONLINE);
  }
}
