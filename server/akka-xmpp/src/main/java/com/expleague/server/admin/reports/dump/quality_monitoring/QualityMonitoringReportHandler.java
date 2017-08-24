package com.expleague.server.admin.reports.dump.quality_monitoring;

import com.expleague.model.Offer;
import com.expleague.server.admin.reports.dump.BaseDumpReportHandler;
import com.expleague.server.admin.reports.dump.DumpVisitor;
import com.expleague.server.admin.reports.dump.quality_monitoring.visitors.OfferVisitor;
import com.expleague.server.admin.reports.dump.visitors.OrdersVisitor;
import com.expleague.server.admin.reports.dump.quality_monitoring.visitors.ClientOrdersVisitor;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.stanza.Message;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User: Artem
 * Date: 21.08.2017
 */
public class QualityMonitoringReportHandler extends BaseDumpReportHandler {
  private ReportRequest reportRequest;

  @ActorMethod
  public void report(ReportRequest reportRequest) {
    this.reportRequest = reportRequest;
    headers("ts", "topic");
    startProcessing(reportRequest.start(), reportRequest.end());
    sender().tell(build(), self());
  }

  @Override
  protected void process(String roomId, List<Message> dump) {
    final OrdersVisitor ordersVisitor = new ClientOrdersVisitor(reportRequest.clientId());
    dump.forEach(ordersVisitor::visit);

    final List<OrdersVisitor.OrderInterval> intervals = ordersVisitor.result();
    final List<DumpVisitor<Offer>> offerDumpVisitors = intervals
        .stream()
        .map(orderInterval -> new OfferVisitor(orderInterval.firstMessageId(), orderInterval.lastMessageId()))
        .collect(Collectors.toList());
    dump.forEach(message -> offerDumpVisitors.forEach(offerDumpVisitor -> offerDumpVisitor.visit(message)));

    offerDumpVisitors.forEach(offerDumpVisitor -> {
      final Offer offer = offerDumpVisitor.result();
      final long ts = (long) (offer.started() * 1000);
      row(Long.toString(ts), offer.topic());
    });
  }

  public static class ReportRequest {
    private final long start;
    private final long end;
    private final String clientId;


    public ReportRequest(long start, long end, String clientId) {
      this.start = start;
      this.end = end;
      this.clientId = clientId;
    }

    long start() {
      return start;
    }

    long end() {
      return end;
    }

    String clientId() {
      return clientId;
    }
  }
}
