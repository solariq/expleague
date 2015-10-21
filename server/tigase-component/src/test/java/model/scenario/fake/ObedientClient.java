package model.scenario.fake;

import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;
import tigase.util.TigaseStringprepException;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:47
 */
public class ObedientClient extends ClientImpl {
  private int chatLength;

  public ObedientClient(int chatLength) throws TigaseStringprepException {
    super("client@localhost");
    this.chatLength = chatLength;
  }

  public ObedientClient() throws TigaseStringprepException {
    this(0);
  }

  @Override
  public void feedback(Room room) {
    super.feedback(room);
    if (chatLength-- > 0) {
      formulating();
      query();
    }
    else
      state(State.ONLINE);
  }
}
