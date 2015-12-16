package tbts.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.actor.*;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.stage.Context;
import akka.stream.stage.PushPullStage;
import akka.stream.stage.SyncDirective;
import com.spbsu.commons.random.FastRandom;
import org.junit.Test;
import scala.runtime.BoxedUnit;

import java.util.Arrays;

/**
 * User: solar
 * Date: 09.12.15
 * Time: 10:46
 */
public class TestStreams {
  private static class RandIntStream extends AbstractActorPublisher<Integer> {
    private final FastRandom rng = new FastRandom();
    public RandIntStream() {
      receive(ReceiveBuilder
          .matchEquals("stop", s -> context().stop(self()))
          .match(ActorPublisherMessage.Request.class, request -> nextRand())
          .match(ActorPublisherMessage.Cancel.class, cancel -> context().stop(self()))
          .build()
      );
    }
    int index = 0;
    public void nextRand() {
      for (int i = 0; i < totalDemand(); i++) {
        onNext(index++);
      }
    }

    @Override
    public void unhandled(Object message) {
      System.out.println(message);
      super.unhandled(message);
    }
  }

  private static class IntPrinter extends AbstractActorSubscriber {
    public IntPrinter() {
      receive(
          ReceiveBuilder
              .match(ActorSubscriberMessage.OnNext.class, onNext -> {
                if (onNext.element() instanceof Integer)
                  play((Integer)onNext.element());
              })
              .build()
      );
    }

    private void play(int element) {
      System.out.println(element);
    }

    @Override
    public RequestStrategy requestStrategy() {
      return new MaxInFlightRequestStrategy(10) {
        @Override
        public int inFlightInternally() {
          return 1;
        }
      };
    }
  }

  private static class SmartFilter extends PushPullStage<Integer, Integer> {
    private int basis;

    private SmartFilter() {
      this.basis = 2;
    }

    @Override
    public SyncDirective onPush(Integer elem, Context<Integer> integerContext) {
      if (elem % basis == 0)
        return integerContext.push(elem);
      return integerContext.pull();
    }

    @Override
    public SyncDirective onPull(Context<Integer> integerContext) {
      return integerContext.pull();
    }
  }

  @Test
  public void testFlows() throws InterruptedException {
    final ActorSystem system = ActorSystem.create("test-env");
    Materializer materializer = ActorMaterializer.create(system);

    Source.from(Arrays.asList(new String[]{"askdjhad"})).to(Sink.foreach(System.out::println)).run(materializer);
    final Source<Integer, ActorRef> source = Source.actorPublisher(Props.create(RandIntStream.class));
    final Sink<Integer, ActorRef> sink = Sink.actorSubscriber(Props.create(IntPrinter.class));
    final Flow<Integer, Integer, BoxedUnit> flow = Flow.of(Integer.class).transform(SmartFilter::new);

//    filter(new Predicate<Integer>(){
//      @Override
//      public boolean test(Integer param) {
//        return param % 2 == 0;
//      }
//    });
    final ActorRef actorRef = source.via(flow).to(sink).run(materializer);

//    actorRef.tell("next", ActorRef.noSender());
//    actorRef.tell("stop", ActorRef.noSender());
    Thread.sleep(100);
  }
}
