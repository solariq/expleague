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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static Pattern deviceNamePattern = Pattern.compile("([0-9a-fA-F]+)-([0-9a-fA-F]+)");

  @Override
  public XMPPDevice register(RegisterQuery query) throws Exception {
    log.log(Level.FINE, "Registering device " + query.username());
    final XMPPDevice device = device(query.username());
    if (device != XMPPDevice.NO_SUCH_DEVICE) {
      if (device.passwd().equals(query.passwd()))
        return device;
      throw new AuthenticationException("User known with different password");
    }
    log.log(Level.INFO, "Registering device " + query.username());
    final String userName;
    final Matcher matcher = deviceNamePattern.matcher(query.username());
    if (matcher.matches())
      userName = matcher.group(1);
    else
      userName = query.username();

    XMPPUser associated = XMPPUser.NO_SUCH_USER;
    try (final PreparedStatement associateUser = createStatement("SELECT * FROM Users WHERE avatar = ? AND avatar IS NOT NULL OR name = ? AND name IS NOT NULL OR id = ?")) {
      associateUser.setString(1, query.avatar());
      associateUser.setString(2, query.name());
      associateUser.setString(3, userName);

      try (final ResultSet resultSet = associateUser.executeQuery()) {
        if (resultSet.next()) {
          associated = createUser(resultSet, 0);
          log.log(Level.INFO, "Have found associated user " + associated.name());
        }
      }
    }
    if (associated == XMPPUser.NO_SUCH_USER) {
      try (final PreparedStatement createUser = createStatement("INSERT INTO Users SET id = ?, country = ?, city = ?, name = ?, avatar = ?, age = ?, sex = ?;")) {
        createUser.setString(1, userName);
        createUser.setString(2, query.country());
        createUser.setString(3, query.city());
        createUser.setString(4, query.name());
        createUser.setString(5, query.avatar());
        createUser.setInt(6, query.age());
        createUser.setInt(7, query.sex());
        createUser.execute();
      }
      usersCache.clear(userName);
      associated = user(userName);
      log.log(Level.INFO, "Created new user " + associated.name());
    }
    try (final PreparedStatement register = createStatement("INSERT INTO Devices SET id = ?, user = ?, passwd = ?, platform = ?, expert = ?;")) {
      register.setString(1, query.username());
      register.setString(2, associated.id());
      register.setString(3, query.passwd());
      register.setString(4, query.platform());
      register.setBoolean(5, query.expert());
      register.execute();
    }
    deviceCache.clear(query.username());
    devicesCache.clear(associated.id());
    return device(query.username());
  }

  private final FixedSizeCache<String, XMPPUser> usersCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);

  public XMPPUser user(String name) {
    return usersCache.get(name, id -> {
      try {
        try (final PreparedStatement userById = createStatement("SELECT * FROM Users WHERE id = ?")) {
          userById.setString(1, id);
          try (final ResultSet resultSet = userById.executeQuery()) {
            if (resultSet.next())
              return createUser(resultSet, 0);
          }
        }
        return XMPPUser.NO_SUCH_USER;
      } catch (SQLException e) {
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
        try (final PreparedStatement devicesByUser = createStatement("SELECT id FROM Devices WHERE user = ?")) {
          devicesByUser.setString(1, userId);
          try (final ResultSet resultSet = devicesByUser.executeQuery()) {
            while (resultSet.next()) {
              result.add(device(resultSet.getString(1)));
            }
          }
        }
        return result.toArray(new XMPPDevice[result.size()]);
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Override
  public Stream<XMPPDevice> allDevices() {
    try {
      return stream("SELECT id FROM Devices", null).map(rs -> {
        try {
          return device(rs.getString(1));
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }).filter(Objects::nonNull);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Stream<XMPPUser> allExperts() {
    try {
      return stream("SELECT Users.id FROM Users JOIN Devices ON Users.id = Devices.user WHERE Devices.expert = TRUE GROUP BY Users.id", null).map(rs -> {
        try {
          return user(rs.getString(1));
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }).filter(Objects::nonNull);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private final FixedSizeCache<String, XMPPDevice> deviceCache = new FixedSizeCache<>(1000, CacheStrategy.Type.LRU);

  @Override
  public XMPPDevice device(String name) {
    return deviceCache.get(name, id -> {
      try {
        try (final PreparedStatement byName = createStatement("SELECT Devices.*, Users.* FROM Devices, Users WHERE Devices.id = ? AND Users.id = Devices.user;")) {
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
              ) {
                @Override
                public void updateDevice(String token, String clientVersion) {
                  this.token = token;
                  this.clientVersion = clientVersion;
                  try {
                    try (final PreparedStatement updateToken = createStatement("UPDATE Devices SET token = ?, platform = ? WHERE id = ?")) {
                      updateToken.setString(1, token);
                      updateToken.setString(2, clientVersion);
                      updateToken.setString(3, id);
                      updateToken.execute();
                    }
                  } catch (SQLException e) {
                    throw new RuntimeException(e);
                  }
                }

                @Override
                public void updateUser(XMPPUser user) {
                  super.updateUser(user);
                  try {
                    try (final PreparedStatement updateToken = createStatement("UPDATE Devices SET `user` = ? WHERE id = ?")) {
                      updateToken.setString(1, user.id());
                      updateToken.setString(2, id);
                      updateToken.execute();
                    }
                  } catch (SQLException e) {
                    throw new RuntimeException(e);
                  }
                }
              };
            }
            else return XMPPDevice.NO_SUCH_DEVICE;
          }
        }
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private final FixedSizeCache<String, ExpertsProfile> profilesCache = new FixedSizeCache<>(100, CacheStrategy.Type.LRU);

  @Override
  public ExpertsProfile profile(String id) {
    return profilesCache.get(id, a -> Roster.super.profile(id));
  }

  @Override
  public void invalidateProfile(JID jid) {
    profilesCache.clear(jid.local());
  }

  @Override
  public Stream<Tag> specializations(JID jid) {
    try {
      return stream("SELECT T.tag, S.score " +
              "FROM Specializations AS S JOIN Tags AS T ON S.tag = T.id " +
              "WHERE `owner` = ?",
          stmt -> stmt.setString(1, jid.local()))
          .map(rs -> {
            try {
              return new Tag(rs.getString(1), rs.getFloat(2));
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void application(Application application, JID referer) {
    try {
      try (final PreparedStatement addApplication = createStatement("INSERT INTO Applications SET referer= ?, email = ?")) {
        addApplication.setString(2, application.email());
        addApplication.setString(1, referer.local());
        addApplication.execute();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

//  @Override
//  public void merge(XMPPUser... users) {
//    if (users.length < 2)
//      return;
//    final XMPPUser main = users[0];
//    Arrays.stream(users).skip(1)
//        .flatMap(user -> {
//          MySQLRoster.this.usersCache.put(user.id(), main);
//          return Arrays.stream(user.devices());
//        })
//        .forEach(device -> device.updateUser(main));
//    final PreparedStatement delete = createStatement("delete-user", "DELETE FROM Users WHERE id = ?");
//    final PreparedStatement updateApplications = createStatement("replace-user-applications", "UPDATE Applications SET referer = ? WHERE referer = ?");
//    final PreparedStatement updateParticipants = createStatement("replace-user-participants", "UPDATE Participants SET partisipant = ? WHERE partisipant = ?");
//    final PreparedStatement updateSpecializations = createStatement("replace-user-specializations", "UPDATE Specializations SET owner = ? WHERE owner = ?");
//    for (int i = 1; i < users.length; i++) {
//      try {
//        updateApplications.setString(1, main.id());
//        updateApplications.setString(2, users[i].id());
//        updateApplications.execute();
//        updateParticipants.setString(1, main.id());
//        updateParticipants.setString(2, users[i].id());
//        updateParticipants.execute();
//        updateSpecializations.setString(1, main.id());
//        updateSpecializations.setString(2, users[i].id());
//        updateSpecializations.execute();
//        delete.setString(1, users[i].id());
//        delete.execute();
//      }
//      catch (SQLException e) {
//        throw new RuntimeException(e);
//      }
//    }
//  }

  @NotNull
  private XMPPUser createUser(ResultSet resultSet, int offset) throws SQLException {
    final int priority = resultSet.getInt(offset + 9);

    return new XMPPUser(
        resultSet.getString(offset + 1),
        resultSet.getString(offset + 2),
        resultSet.getString(offset + 3),
        resultSet.getString(offset + 4),
        resultSet.getInt(offset + 5),
        resultSet.getInt(offset + 6),
        resultSet.getTimestamp(offset + 7),
        resultSet.getString(offset + 8),
        ExpertsProfile.Authority.valueOf(priority)
    );
  }
}
