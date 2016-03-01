package com.expleague.server.agents;

import akka.actor.*;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import com.expleague.server.ExpLeagueServerTestCase;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.util.akka.AkkaTools;
import com.expleague.xmpp.JID;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.system.RuntimeUtils;
import com.typesafe.config.Config;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
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

    private final Map<JID, ActorAdapter> jidMocks = new HashMap<>();

    public TestKit() {
      super(system);
      xmpp = system.actorOf(Props.create(XMPPWithMockSupport.class, this), "xmpp");
      registrator = system.actorOf(Props.create(Registrator.class));
    }

    protected ActorRef register(final JID jid) {
      registrator.tell(jid, getRef());
      return expectActorRef();
    }

    protected ActorRef register(final JID jid, final ActorAdapter actorMock) {
      jidMocks.put(jid, actorMock);
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

    public class XMPPWithMockSupport extends XMPP {
      @NotNull
      @Override
      protected Props newActorProps(final JID jid) {
        final ActorAdapter actorMock = jidMocks.get(jid);
        if (actorMock != null) {
          return Props.create(UntypedActorMock.class, actorMock);
        }
        return super.newActorProps(jid);
      }
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

  public static class UntypedActorMock extends UntypedActor {
    private final RuntimeUtils.InvokeDispatcher dispatcher;
    private final ActorAdapter actorMock;

    public UntypedActorMock(final ActorAdapter dispatchTrait) {
      dispatcher = new RuntimeUtils.InvokeDispatcher(dispatchTrait.getClass(), dispatchTrait::unhandled, ActorMethod.class);
      try {
        actorMock = dispatchTrait;
        actorMock.injectActor(this);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void onReceive(final Object message) throws Exception {
      dispatcher.invoke(actorMock, message);
    }
  }
}
