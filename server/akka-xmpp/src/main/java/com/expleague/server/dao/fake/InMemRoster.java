package com.expleague.server.dao.fake;

import com.expleague.model.Application;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.XMPPUser;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;

import javax.security.sasl.AuthenticationException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 25.12.15
 * Time: 7:38
 */
@SuppressWarnings("unused")
public class InMemRoster implements Roster {
  private static final Logger log = Logger.getLogger(InMemRoster.class.getName());
  final Map<String, XMPPDevice> devices = new HashMap<>();
  final Map<String, XMPPUser> users = new HashMap<>();
  @Override
  public RegisterQuery required() {
    final RegisterQuery query = new RegisterQuery();
    query.username("");
    query.passwd("");
    return query;
  }

  @Override
  public synchronized XMPPDevice register(RegisterQuery query) throws Exception {
    final XMPPDevice device = device(query.username());
    if (device != null) {
      if (device.passwd().equals(query.passwd()))
        return device;
      throw new AuthenticationException("User known with different password");
    }
    log.log(Level.INFO, "Registering device " + query.username());
    XMPPUser associated = null;
    for (XMPPUser user : users.values()) {
      if (user.avatar() != null && user.avatar().equals(query.avatar()) ||
          user.name() != null && user.name().equals(query.name())) {
        associated = user;
        break;
      }
    }
    if (associated == null) {
      associated = new XMPPUser(query.username(), query.country(), query.city(), query.name(), 0, 0, new Date(), query.avatar());
      users.put(associated.id(), associated);
      log.log(Level.INFO, "Created new user " + associated.name());
    }
    final XMPPDevice result = new XMPPDevice(associated, query.username(), query.passwd(), query.expert(), query.platform(), null) {
      @Override
      public void updateToken(String token) {
        this.token = token;
      }
    };
    devices.put(result.name(), result);
    return result;
  }

  @Override
  public synchronized XMPPDevice device(String name) {
    return devices.get(name);
  }

  @Override
  public synchronized XMPPUser user(String name) {
    return users.get(name);
  }

  @Override
  public synchronized XMPPDevice[] devices(String id) {
    final List<XMPPDevice> result = devices.values().stream().filter(
        device -> id.equals(device.user().id())
    ).collect(Collectors.toList());
    return result.toArray(new XMPPDevice[result.size()]);
  }

  @Override
  public Stream<XMPPDevice> allDevices() {
    return devices.values().stream();
  }

  @Override
  public void invalidateProfile(JID jid) {
  }

  private final List<Application> applications = new ArrayList<>();
  @Override
  public void application(Application application, JID referer) {
    applications.add(application);
  }
}
