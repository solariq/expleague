package model.scenario.fake;

import com.tbts.model.Room;
import com.tbts.model.impl.ExpertImpl;

/**
 * User: solar
 * Date: 04.10.15
 * Time: 20:08
 */
public class ObedientExpert extends ExpertImpl {
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
    ask();
  }

  @Override
  public void ask() {
    super.ask();
    Room active = active();
    if (active == null)
      throw new RuntimeException();
    active.addListener(room -> {
      if (room.state() == Room.State.LOCKED) {
        System.out.println("Answering to " + room.id());
        room.answer();
      }
    });
  }
}
