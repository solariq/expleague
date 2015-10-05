package model.scenario.fake;

import com.tbts.model.Answer;
import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 20:08
 */
public class ObedientExpert extends ExpertImpl {
  public ObedientExpert() throws TigaseStringprepException {
    super(BareJID.bareJIDInstance("expert@localhost"));
  }

  @Override
  public void ask(Room room) {
    super.ask(room);
    answer();
    room.answer(new Answer());
  }
}
