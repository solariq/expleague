package com.expleague.server;

import akka.actor.ActorSystem;
import com.expleague.server.admin.ExpLeagueAdminService;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.XMPP;
import com.expleague.server.dao.Archive;
import com.expleague.server.dao.PatternsRepository;
import com.expleague.server.services.XMPPServices;
import com.expleague.server.xmpp.XMPPClientConnection;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.server.notifications.NotificationsManager;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.duration.FiniteDuration;

import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
@SuppressWarnings("unused")
public class ExpLeagueServer {
//  private static final Logger log = Logger.getLogger(ExpLeagueServer.class.getName());
  private static Cfg config;
  private static Roster users;
  private static LaborExchange.Board leBoard;
  private static Archive archive;
  private static PatternsRepository patterns;

  public static void main(String[] args) throws Exception {
    final Config load = ConfigFactory.load();
    setConfig(new ServerCfg(load));

    final ActorSystem system = ActorSystem.create("ExpLeague", load);

    // singletons
    system.actorOf(ActorAdapter.props(XMPP.class), "xmpp");
    system.actorOf(ActorAdapter.props(LaborExchange.class), "labor-exchange");

    // per node
    system.actorOf(ActorAdapter.props(NotificationsManager.class, config.iosPushCert(), config.iosPushPasswd()), "notifications");
    system.actorOf(ActorAdapter.props(XMPPServices.class), "services");
    system.actorOf(ActorAdapter.props(XMPPServer.class), "comm");
    system.actorOf(ActorAdapter.props(BOSHServer.class), "bosh");
    system.actorOf(ActorAdapter.props(ImageStorage.class), "image-storage");
    system.actorOf(ActorAdapter.props(ExpLeagueAdminService.class, load.getConfig("tbts.admin.embedded")), "admin-service");
  }

  public static Roster roster() {
    return users;
  }
  public static LaborExchange.Board board() {
    return leBoard;
  }
  public static Archive archive() {
    return archive;
  }
  public static PatternsRepository patterns() {
    return patterns;
  }

  public static Cfg config() {
    return config;
  }

  @VisibleForTesting
  public static void setConfig(final Cfg cfg) throws Exception {
    config = cfg;
    ActorAdapter.cfg = cfg;
    XMPPClientConnection.cfg = cfg;
    users = config.roster().newInstance();
    leBoard = config.board().newInstance();
    archive = config.archive().newInstance();
    patterns = config.patterns().newInstance();
    if (System.getProperty("logger.config") == null)
      LogManager.getLogManager().readConfiguration(ExpLeagueServer.class.getResourceAsStream("/logging.properties"));
    else
      LogManager.getLogManager().readConfiguration(new FileInputStream(System.getProperty("logger.config")));
  }

  public interface Cfg {
    ServerCfg.Type type();

    boolean unitTest();

    String domain();

    String db();

    Class<? extends Archive> archive();

    Class<? extends Roster> roster();

    Class<? extends LaborExchange.Board> board();

    Class<? extends PatternsRepository> patterns();

    Config config();

    default FiniteDuration timeout(final String name) {
      final Config config = config().getConfig(name);
      if (config == null) {
        throw new IllegalArgumentException("No timeout configured for: " + name);
      }
      return FiniteDuration.create(
        config.getLong("length"),
        TimeUnit.valueOf(config.getString("unit"))
      );
    }

    default TimeUnit timeUnit(final String name) {
      return TimeUnit.valueOf(config().getString(name + ".unit"));
    }

    String iosPushCert();
    String iosPushPasswd();

    String dynamoDB();

    enum Type {
      PRODUCTION,
      TEST
    }
  }

  public static class ServerCfg implements Cfg {
    private final Config config;

    private final String db;
    private final String domain;
    private final Class<? extends Archive> archive;
    private final Class<? extends Roster> roster;
    private final Class<? extends LaborExchange.Board> board;
    private final Class<? extends PatternsRepository> patterns;
    private final Type type;
    private final boolean unitTest;

    public ServerCfg(Config load) throws ClassNotFoundException {
      config = load.getConfig("tbts");
      db = config.getString("db");
      domain = config.getString("domain");
      //noinspection unchecked
      archive = (Class<? extends Archive>) Class.forName(config.getString("archive"));
      //noinspection unchecked
      board = (Class<? extends LaborExchange.Board>) Class.forName(config.getString("board"));
      //noinspection unchecked
      roster = (Class<? extends Roster>) Class.forName(config.getString("roster"));
      //noinspection unchecked
      patterns = (Class<? extends PatternsRepository>) Class.forName(config.getString("patterns"));
      type = Type.valueOf(config.getString("type").toUpperCase());
      unitTest = config.hasPath("unit-test") && config.getBoolean("unit-test");
    }

    @Override
    public Config config() {
      return config;
    }

    @Override
    public String iosPushCert() {
      return config.getString("notifications.ios.cert");
    }

    @Override
    public String iosPushPasswd() {
      return config.getString("notifications.ios.passwd");
    }

    @Override
    public String dynamoDB() {
      final String string = config.getString("dynamo.db");
      return string != null ? string : "expleague-rooms-test";
    }

    @Override
    public String domain() {
      return domain;
    }

    @Override
    public String db() {
      return db;
    }

    @Override
    public Class<? extends Archive> archive() {
      return archive;
    }

    @Override
    public Class<? extends Roster> roster() {
      return roster;
    }

    @Override
    public Class<? extends PatternsRepository> patterns() {
      return patterns;
    }

    @Override
    public Type type() {
      return type;
    }

    @Override
    public boolean unitTest() {
      return unitTest;
    }

    @Override
    public Class<? extends LaborExchange.Board> board() {
      return board;
    }
  }
}
