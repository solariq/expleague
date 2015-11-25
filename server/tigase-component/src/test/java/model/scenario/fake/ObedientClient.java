package model.scenario.fake;

import com.tbts.model.Room;
import com.tbts.model.impl.ClientImpl;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 19:47
 */
public class ObedientClient extends ClientImpl {
  private int chatLength;

  public ObedientClient(String id, int chatLength) {
    super(id);
    this.chatLength = chatLength;
  }

  public ObedientClient(String id){
    this(id, 0);
  }

  @Override
  public void feedback(Room room) {
    super.feedback(room);
    if (chatLength-- > 0) {
      query();
    }
    else
      state(State.ONLINE);
  }
}
