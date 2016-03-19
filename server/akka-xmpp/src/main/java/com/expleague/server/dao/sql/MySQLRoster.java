package com.expleague.server.dao.sql;

import com.expleague.model.Application;
import com.expleague.model.ExpertsProfile;
import com.expleague.model.Tag;
import com.expleague.server.ExpLeagueServer;
import com.expleague.server.Roster;
import com.expleague.server.XMPPDevice;
import com.expleague.server.XMPPUser;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.control.register.RegisterQuery;
import com.spbsu.commons.util.cache.CacheStrategy;
import com.spbsu.commons.util.cache.impl.FixedSizeCache;
import org.jetbrains.annotations.NotNull;

import javax.security.sasl.AuthenticationException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * User: solar
 * Date: 11.12.15
 * Time: 22:37
 */
@SuppressWarnings("unused")
public class MySQLRoster extends MySQLOps implements Roster {
  private static final Logger log = Logger.getLogger(MySQLRoster.class.getName());
  public MySQLRoster() {
    super(ExpLeagueServer.config().db());
  }

  @Override
  public RegisterQuery required() {
    return RegisterQuery.requiredFields();
  }

  @Override
  public XMPPDevice register(RegisterQuery query) throws Exception {
    log.log(Level.FINE, "Registering device " + query.username());
    final XMPPDevice device = device(query.username());
    if (device != null) {
      if (device.passwd().equals(query.passwd()))
        return device;
      throw new AuthenticationException("User known with different password");
    }
    log.log(Level.INFO, "Registering device " + query.username());
    XMPPUser associated = null;
    final PreparedStatement associateUser = createStatement("associate-user",
        "SELECT * FROM expleague.Users WHERE avatar = ? AND avatar IS NOT NULL OR name = ? AND name IS NOT NULL OR id = ?"
    );
    associateUser.setString(1, query.avatar());
    associateUser.setString(2, query.name());
    associateUser.setString(3, query.username());

    try (final ResultSet resultSet = associateUser.executeQuery()) {
      if (resultSet.next()) {
        associated = createUser(resultSet, 0);
        log.log(Level.INFO, "Have found associated user " + associated.name());
      }
    }
    if (associated == null) {
      final PreparedStatement createUser = createStatement("create-user",
          "INSERT INTO expleague.Users SET id = ?, country = ?, city = ?, name = ?, avatar = ?, age = ?, sex = ?;"
      );
      createUser.setString(1, query.username());
      createUser.setString(2, query.country());
      createUser.setString(3, query.city());
      createUser.setString(4, query.name());
      createUser.setString(5, query.avatar());
      createUser.setInt(6, query.age());
      createUser.setInt(7, query.sex());
      createUser.execute();
      associated = user(query.username());
      log.log(Level.INFO, "Created new user " + associated.name());
    }
    final PreparedStatement register = createStatement("create-device",
        "INSERT INTO expleague.Devices SET id = ?, user = ?, passwd = ?, platform = ?, expert = ?;"
    );
    register.setString(1, query.username());
    register.setString(2, associated.id());
    register.setString(3, query.passwd());
    register.setString(4, query.platform());
    register.setBoolean(5, query.expert());
    register.execute();
    devicesCache.clear(associated.id());
    return device(query.username());
  }

  private final FixedSizeCache<String, XMPPUser> usersCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  public XMPPUser user(String name) {
    return usersCache.get(name, id -> {
      try {
        final PreparedStatement userById = createStatement("user-by-name",
            "SELECT * FROM expleague.Users WHERE id = ?"
        );
        userById.setString(1, id);
        try (final ResultSet resultSet = userById.executeQuery()) {
          if (resultSet.next())
            return createUser(resultSet, 0);
        }
        return null;
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private final FixedSizeCache<String, XMPPDevice[]> devicesCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  @Override
  public XMPPDevice[] devices(String userId) {
    return devicesCache.get(userId, id -> {
      try {
        final List<XMPPDevice> result = new ArrayList<>();
        final PreparedStatement devicesByUser = createStatement("devices-by-user",
            "SELECT id FROM expleague.Devices WHERE user = ?"
        );
        devicesByUser.setString(1, userId);
        try (final ResultSet resultSet = devicesByUser.executeQuery()) {
          while (resultSet.next()) {
            result.add(device(resultSet.getString(1)));
          }
        }
        return result.toArray(new XMPPDevice[result.size()]);
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private final FixedSizeCache<String, XMPPDevice> deviceCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);
  @Override
  public XMPPDevice device(String name) {
    return deviceCache.get(name, id -> {
      try {
        final PreparedStatement byName = createStatement("device-by-name",
            "SELECT Devices.*, Users.* FROM expleague.Devices, expleague.Users WHERE Devices.id = ? AND Users.id = Devices.user;"
        );
        byName.setString(1, id);
        try (final ResultSet resultSet = byName.executeQuery()) {
          if (resultSet.next()) {
            return new XMPPDevice(
                createUser(resultSet, 6),
                id,
                resultSet.getString(4),
                resultSet.getBoolean(6),
                resultSet.getString(5),
                resultSet.getString(3)
            ){
              @Override
              public void updateToken(String token) {
                this.token = token;
                final PreparedStatement updateToken = createStatement("update-token", "UPDATE expleague.Devices SET token = ? WHERE id = ?");
                try {
                  updateToken.setString(1, token);
                  updateToken.setString(2, id);
                  updateToken.execute();
                }
                catch (SQLException e) {
                  throw new RuntimeException(e);
                }
              }
            };
          }
          else return null;
        }
      }
      catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private final FixedSizeCache<String, ExpertsProfile> profilesCache = new FixedSizeCache<>(100, CacheStrategy.Type.LRU);
  @Override
  public ExpertsProfile profile(JID jid) {
    return profilesCache.get(jid.local(), a -> Roster.super.profile(jid));
  }

  @Override
  public void invalidateProfile(JID jid) {
    profilesCache.clear(jid.local());
  }

  @Override
  public Stream<Tag> specializations(JID jid) {
    return stream("specializations", "SELECT T.tag, S.score " +
        "FROM expleague.Specializations AS S JOIN expleague.Tags AS T ON S.tag = T.id " +
        "WHERE `owner` = ?",
        stmt -> stmt.setString(1, jid.local()))
        .map(rs -> {
      try {
        return new Tag(rs.getString(1), rs.getFloat(2));
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public void application(Application application, JID referer) {
    try {
      final PreparedStatement addApplication = createStatement("add-application", "INSERT INTO expleague.Applications SET referer= ?, email = ?");
      addApplication.setString(1, application.email());
      addApplication.setString(2, referer.bare().toString());
    }
    catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private XMPPUser createUser(ResultSet resultSet, int offset) throws SQLException {
    return new XMPPUser(
        resultSet.getString(offset + 1),
        resultSet.getString(offset + 2),
        resultSet.getString(offset + 3),
        resultSet.getString(offset + 4),
        resultSet.getInt(offset + 5),
        resultSet.getInt(offset + 6),
        resultSet.getTimestamp(offset + 7),
        resultSet.getString(offset + 8)
    );
  }
}
