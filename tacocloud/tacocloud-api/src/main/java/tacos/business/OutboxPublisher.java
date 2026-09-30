package tacos.business;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import tacos.messaging.OrderMessagingService;
@Component @EnableScheduling
public class OutboxPublisher {
  private final ReactiveMongoTemplate mongo;private final OrderMessagingService transport;private final Clock clock;
  private final int maximum;private final AtomicBoolean running=new AtomicBoolean();
  public OutboxPublisher(ReactiveMongoTemplate mongo,OrderMessagingService transport,Clock clock,
      @Value("${tacocloud.outbox.max-attempts:8}") int maximum) { this.mongo=mongo;this.transport=transport;this.clock=clock;this.maximum=maximum; }
  public Mono<Boolean> publishOne() {
    return Mono.defer(()->{
    Instant now=clock.instant();String token=UUID.randomUUID().toString();
    Criteria eligible=new Criteria().orOperator(Criteria.where("state").is("NEW").and("nextAttempt").lte(now),
        Criteria.where("state").is("PUBLISHING").and("leaseUntil").lte(now));
    Query query=Query.query(eligible).with(Sort.by("createdAt","_id"));
    return mongo.findAndModify(query,new Update().set("state","PUBLISHING").set("claimToken",token)
        .set("leaseUntil",now.plusSeconds(60)).inc("attempts",1),FindAndModifyOptions.options().returnNew(true),OutboxEvent.class)
      .flatMap(row-> {
        if(row.getAttempts()>maximum) return mongo.updateFirst(owned(row,token),new Update().set("state","FAILED").set("lastError","CLAIM_RETRY_EXHAUSTED"),OutboxEvent.class).thenReturn(false);
        return Mono.defer(()->Mono.fromCompletionStage(transport.sendOrder(row.getPayload())))
        .subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(20))
        .then(mongo.updateFirst(owned(row,token),new Update().set("state","PUBLISHED").set("publishedAt",clock.instant()).unset("lastError").unset("leaseUntil"),OutboxEvent.class)).thenReturn(true)
        .onErrorResume(error->mongo.updateFirst(owned(row,token),new Update().set("state",row.getAttempts()>=maximum?"FAILED":"NEW")
            .set("lastError","TRANSPORT_FAILURE").set("nextAttempt",clock.instant().plusSeconds(Math.min(300,1L<<Math.min(row.getAttempts(),8))))
            .unset("leaseUntil"),OutboxEvent.class).thenReturn(false)); }).defaultIfEmpty(false);
    });
  }
  private Query owned(OutboxEvent row,String token) { return Query.query(Criteria.where("_id").is(row.getId()).and("claimToken").is(token)); }
  @Scheduled(fixedDelayString="${tacocloud.outbox.interval-ms:1000}")
  public void tick() {
    if(running.compareAndSet(false,true)) {
      // Borde scheduler: la suscripcion inicia el trabajo y libera el control al terminar.
      Flux.range(0,20).concatMap(i->publishOne()).doFinally(signal->running.set(false)).subscribe(v->{},e->org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox polling failed"));
    }
  }
}
