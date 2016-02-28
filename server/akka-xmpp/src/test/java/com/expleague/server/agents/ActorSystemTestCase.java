package com.expleague.server.agents;

import akka.actor.*;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import com.expleague.server.ExpLeagueServerTestCase;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.spbsu.commons.io.StreamTools;
import com.typesafe.config.Config;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

/**
 * @author vpdelta
 */
public class ActorSystemTestCase {
  protected ActorSystem system;

  @Before
  public void setUp() throws Exception {
    final Config config = ExpLeagueServerTestCase.setUpTestConfig();

    // todo: this is hack while waiting for release of persistence testkit https://github.com/akka/akka/issues/15571
    StreamTools.deleteDirectoryContents(new File(config.getString("akka.persistence.journal.leveldb.dir")));
    StreamTools.deleteDirectoryContents(new File(config.getString("akka.persistence.snapshot-store.local.dir")));

    system = ActorSystem.create("test-env");
  }

  @After
  public void tearDown() {
    JavaTestKit.shutdownActorSystem(system);
  }

  public class TestKit extends JavaTestKit {
    protected final ActorRef xmpp;
    protected final ActorRef registrator;

    public TestKit() {
      super(system);
      xmpp = system.actorOf(Props.create(XMPP.class), "xmpp");
      registrator = system.actorOf(Props.create(Registrator.class));
    }

    protected ActorRef register(final JID jid) {
      registrator.tell(jid, getRef());
      return expectActorRef();
    }

    protected ActorRef expectActorRef() {
      return expectMessage(ActorRef.class);
    }

    protected <T> T expectMessage(final Class<T> cls) {
      return this.new ExpectMsg<T>(cls.getSimpleName() + " is returned") {
        @Override
        protected T match(final Object o) {
          if (cls.isAssignableFrom(o.getClass())) {
            return (T) o;
          }
          throw noMatch();
        }
      }.get();
    }
  }

  public static class ActorFinder extends UntypedActor {
    @Override
    public void onReceive(final Object o) throws Exception {
      final ActorSelection actorSelection = context().actorSelection((String) o);
      final Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
      final ActorIdentity actorIdentity = AkkaTools.askOrThrow(actorSelection, new Identify(1), timeout);
      sender().tell(actorIdentity.getRef(), self());
    }
  }

  public static class Registrator extends UntypedActor {
    @Override
    public void onReceive(final Object o) throws Exception {
      sender().tell(XMPP.register((JID) o, context()), self());
    }
  }
}
