package model.scenario.fake;

import com.tbts.model.impl.ClientImpl;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:47
 */
public class ObedientClient extends ClientImpl {
  private int chatLength;

  public ObedientClient(int chatLength) throws TigaseStringprepException {
    super(BareJID.bareJIDInstance("client@localhost"));
    this.chatLength = chatLength;
  }

  public ObedientClient() throws TigaseStringprepException {
    this(0);
  }

  @Override
  public void feedback() {
    super.feedback();
    if (chatLength-- > 0)
      state(State.CHAT);
    else
      state(State.ONLINE);
  }
}
