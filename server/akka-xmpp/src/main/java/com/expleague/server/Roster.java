package com.expleague.server;

import com.expleague.model.Application;
import com.expleague.model.ExpertsProfile;
import com.expleague.model.Tag;
import com.expleague.server.agents.LaborExchange;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.expleague.server.agents.ExpLeagueOrder.Role.ACTIVE;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 18:37
 */
public interface Roster {
  XMPPDevice register(RegisterQuery query) throws Exception;
  RegisterQuery required();

  XMPPDevice device(String name);
  XMPPUser user(String name);
  XMPPDevice[] devices(String id);

  Stream<XMPPDevice> allDevices();

  static Roster instance() {
    return ExpLeagueServer.roster();
  }

  default ExpertsProfile profile(String id) {
    final XMPPUser user = user(id);
    final JID jid = user.jid();
    final ExpertsProfile.Builder builder = new ExpertsProfile.Builder(jid);

    builder.name(user.name())
        .avatar(user.avatar());
    user.tags().forEach(tag -> builder.tag(tag.name(), tag.score()));

    final int tasks = LaborExchange.board().related(jid)
        .filter(o -> o.role(jid) == ACTIVE)
        .map(o -> {
          final double feedback = o.feedback();
          builder.score(feedback);
          if (feedback > 0) {
            Arrays.stream(o.tags()).forEach(tag ->
                builder.tag(tag.name(), feedback)
            );
          }
          o.tags();
          return o;
        }).collect(Collectors.counting()).intValue();

    builder.tasks(tasks);

    return builder.build();
  }

  default Stream<JID> favorites(JID from) {
    return LaborExchange.board()
        .related(from)
        .map(o -> o.of(ACTIVE)).flatMap(s -> s);
  }

  void invalidateProfile(JID jid);

  default Stream<Tag> specializations(JID jid) {
    final ExpertsProfile.Builder builder = new ExpertsProfile.Builder(jid);
    LaborExchange.board().related(jid)
        .filter(o -> o.role(jid) == ACTIVE)
        .forEach(o -> {
          final double feedback = o.feedback();
          if (feedback > 0) {
            Arrays.stream(o.tags()).forEach(
                tag -> builder.tag(tag.name(), feedback)
            );
          }
        });
    return builder.build().tags();
  }

  void application(Application application, JID referer);

//  void merge(XMPPUser... users);
}
