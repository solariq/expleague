package model.scenario.fake;

import com.spbsu.commons.func.Action;
import com.tbts.model.Answer;
import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 20:08
 */
public class ObedientExpert extends ExpertImpl {
  private Action<Room> roomAction = new Action<Room>() {
    @Override
    public void invoke(Room room) {
      if (room.state() == Room.State.LOCKED) {
        answer(new Answer());
        room.removeListener(this);
      }
    }
  };


  public ObedientExpert(String id) {
    super(id);
  }

  @Override
  public boolean reserve(Room room) {
    boolean reserve = super.reserve(room);
    if (reserve) {
      steady();
    }
    return reserve;
  }

  @Override
  public void invite() {
    super.invite();
    ask(active());
    active().addListener(roomAction);
  }
}
