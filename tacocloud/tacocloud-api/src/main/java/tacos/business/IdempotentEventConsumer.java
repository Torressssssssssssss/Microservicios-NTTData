package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import reactor.core.publisher.Mono;
import tacos.ProcessedEvent;
import tacos.messaging.*;
import java.time.Clock;
import java.util.UUID;
@Service
public class IdempotentEventConsumer {
  public static class PermanentEventException extends RuntimeException { public PermanentEventException(String reason) { super(reason); } }
  private final ReactiveMongoTemplate mongo;private final TransactionalOperator tx;private final Clock clock;
  public IdempotentEventConsumer(ReactiveMongoTemplate mongo,TransactionalOperator tx,Clock clock) { this.mongo=mongo;this.tx=tx;this.clock=clock; }
  public static void validate(OrderEvent e) {
    if(e==null || e.getVersion()!=1 || e.getEventType()==null || e.getPayload()==null || e.getPayload().getOrderId()==null
        || e.getPayload().getStatus()==null || e.getCorrelationId()==null || e.getOccurredAt()==null)
      throw new PermanentEventException("UNSUPPORTED_EVENT");
    try { UUID.fromString(e.getEventId());java.time.Instant.parse(e.getOccurredAt());
      tacos.TacoOrder.Status.valueOf(e.getPayload().getStatus());
      if(!e.getCorrelationId().matches("[A-Za-z0-9_-]{1,64}") || e.getPayload().getOrderVersion()<0)throw new IllegalArgumentException(); }
    catch(Exception error) { throw new PermanentEventException("INVALID_EVENT_METADATA"); }
  }
  public Mono<Boolean> consume(OrderEvent event) {
    return Mono.defer(()->{
      validate(event);
      return tx.transactional(mongo.exists(Query.query(Criteria.where("_id").is(event.getEventId())),ProcessedEvent.class).flatMap(done->{
        if(done) return Mono.just(false);
        return mongo.findById(event.getPayload().getOrderId(),KitchenTicket.class).defaultIfEmpty(new KitchenTicket()).flatMap(ticket->{
          Mono<?> effect=Mono.empty();
          if(ticket.getId()==null || ticket.getOrderVersion()<event.getPayload().getOrderVersion()) {
            ticket.setId(event.getPayload().getOrderId());ticket.setOrderVersion(event.getPayload().getOrderVersion());ticket.setPayload(event.getPayload());effect=mongo.save(ticket);
          }
          ProcessedEvent record=new ProcessedEvent();record.setId(event.getEventId());record.setProcessedAt(clock.instant());record.setResult("APPLIED_OR_STALE");
          return effect.then(mongo.insert(record)).thenReturn(true);
        });
      })).onErrorResume(org.springframework.dao.DuplicateKeyException.class,error->
        mongo.exists(Query.query(Criteria.where("_id").is(event.getEventId())),ProcessedEvent.class)
          .flatMap(done->done?Mono.just(false):Mono.error(error)));
    });
  }
}
