package com.expleague.server;

import com.expleague.model.ExpertsProfile;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.xmpp.JID;

import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 19.12.15
 * Time: 15:12
 */
public class ExpertManager {
  private static ExpertManager instance = new ExpertManager();
  public static ExpertManager instance() {
    return instance;
  }

  public synchronized ExpertsProfile profile(JID jid) {
    final int tasks = LaborExchange.board().related(jid).filter(o -> o.role(jid) == ExpLeagueOrder.Role.ACTIVE).collect(Collectors.counting()).intValue();
    final XMPPDevice device = Roster.instance().device(jid.local());

    return new ExpertsProfile(device.realName(), jid.local(), device.avatar(), tasks);
  }
}
