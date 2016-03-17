package com.expleague.server.agents;

import akka.actor.*;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import com.expleague.server.ExpLeagueServerTestCase;
import com.expleague.util.akka.*;
import com.expleague.xmpp.JID;
import com.spbsu.commons.io.StreamTools;
import com.spbsu.commons.system.RuntimeUtils;
import com.spbsu.commons.util.ThreadTools;
import com.typesafe.config.Config;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author vpdelta
 */
public class ActorSystemTestCase {

  protected ActorSystem system;

  @Before
  public void setUp() throws Exception {
    final Config config = ExpLeagueServerTestCase.setUpTestConfig();

    // todo: this is hack while waiting for release of persistence testkit https://github.com/akka/akka/issues/15571
    StreamTools.deleteDirectoryContents(new File(config.getString("tbts.xmpp.journal.root")));
    StreamTools.deleteDirectoryContents(new File(config.getString("akka.persistence.snapshot-store.local.dir")));

    system = ActorSystem.create("test-env");
  }

  @After
  public void tearDown() {
    JavaTestKit.shutdownActorSystem(system);
  }

  public class TestKit extends JavaTestKit {
    protected final ActorRef xmpp;
    protected final ActorRef laborExchange;
    protected final ActorRef registrator;

    private final Map<JID, ActorAdapter> jidMocks = new HashMap<>();
    private final Map<JID, ActorAdapter> expertMocks = new HashMap<>();
    private final Map<JID, ActorAdapter> jidOverrides = new HashMap<>();

    public TestKit() {
      super(system);
      xmpp = system.actorOf(Props.create(XMPPWithMockSupport.class, this), "xmpp");
      laborExchange = system.actorOf(Props.create(LaborExchange.class), "labor-exchange");
      registrator = system.actorOf(Props.create(Registrator.class));
      ThreadTools.sleep(500);
    }

    protected ActorRef register(final JID jid) {
      registrator.tell(jid, getRef());
      return expectActorRef();
    }

    protected ActorRef registerMock(final JID jid, final ActorAdapter actorMock) {
      jidMocks.put(jid, actorMock);
      registrator.tell(jid, getRef());
      return expectActorRef();
    }

    protected ActorRef registerExpertMock(final JID jid, final ActorAdapter actorMock) {
      expertMocks.put(jid, actorMock);
      return expectActorRef();
    }

    protected ActorRef registerOverride(final JID jid, final ActorAdapter actorMock) {
      jidOverrides.put(jid, actorMock);
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
        final ActorAdapter overrideAdapter = jidOverrides.get(jid);
        if (overrideAdapter != null) {
          final Class<? extends ActorAdapter> overrideAdapterClass = overrideAdapter.getClass();
          final Field[] declaredFields = overrideAdapterClass.getDeclaredFields();
          final List<Object> args = new ArrayList<>();
          for (Field declaredField : declaredFields) {
            declaredField.setAccessible(true);
            if (declaredField.isSynthetic()) {
              try {
                args.add(declaredField.get(overrideAdapter));
              } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
              }
            }
          }
          args.add(jid);
          final Object[] argsArray = args.toArray(new Object[args.size()]);
          return jid.domain().startsWith("muc.")
            ? ActorContainer.props(
                AdapterProps.create(ExpLeagueRoomAgent.class, jid),
                AdapterProps.create(overrideAdapterClass, argsArray)
              )
            : PersistentActorContainer.props(
                AdapterProps.create(UserAgent.class, jid),
                AdapterProps.create(overrideAdapterClass, argsArray)
              );
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
      MessageCapture.instance().capture(sender(), self(), message);

      dispatcher.invoke(actorMock, message);
    }
  }
}
