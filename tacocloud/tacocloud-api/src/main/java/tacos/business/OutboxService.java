package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;
import java.time.Clock;
import tacos.TacoOrder;
import tacos.messaging.*;
@Service
public class OutboxService {
  private final ReactiveMongoTemplate mongo;private final EventFactory events;private final Clock clock;
  public OutboxService(ReactiveMongoTemplate mongo,EventFactory events,Clock clock) { this.mongo=mongo;this.events=events;this.clock=clock; }
  public Mono<TacoOrder> append(TacoOrder order,OrderEventType type) {
    return Mono.deferContextual(context->{
      OrderEvent event=events.create(order,type);
      event.setCorrelationId(context.getOrDefault(tacos.security.Correlation.KEY,java.util.UUID.randomUUID().toString()));
      try(org.slf4j.MDC.MDCCloseable scope=org.slf4j.MDC.putCloseable(tacos.security.Correlation.KEY,event.getCorrelationId())) {
        org.slf4j.LoggerFactory.getLogger(OutboxService.class).info("outbox_append type={} correlationId={}",type,event.getCorrelationId());
      }OutboxEvent row=new OutboxEvent();row.setId(event.getEventId());row.setPayload(event);
      row.setCreatedAt(clock.instant());row.setNextAttempt(clock.instant());return mongo.insert(row).thenReturn(order);
    });
  }
}
