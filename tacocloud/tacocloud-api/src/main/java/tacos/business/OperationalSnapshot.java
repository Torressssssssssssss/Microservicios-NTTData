package tacos.business;
import java.util.concurrent.atomic.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
@Component
public class OperationalSnapshot {
  private final ReactiveMongoTemplate mongo;private final AnnouncementService announcements;
  private final AtomicLong pending=new AtomicLong(),queue=new AtomicLong(),failed=new AtomicLong(),dlq=new AtomicLong();
  private final AtomicBoolean refreshing=new AtomicBoolean();
  public OperationalSnapshot(ReactiveMongoTemplate mongo,AnnouncementService announcements,MeterRegistry registry) {
    this.mongo=mongo;this.announcements=announcements;
    registry.gauge("tacocloud.outbox.pending",pending);registry.gauge("tacocloud.kitchen.queue",queue);
    registry.gauge("tacocloud.outbox.failed",failed);registry.gauge("tacocloud.dlq.pending",dlq);
  }
  public Mono<Void> refresh() {
    return Mono.zip(mongo.count(Query.query(Criteria.where("state").in("NEW","PUBLISHING")),OutboxEvent.class),
      mongo.count(Query.query(Criteria.where("status").is(TacoOrder.Status.CREATED)),TacoOrder.class),
      mongo.count(Query.query(Criteria.where("state").is("FAILED")),OutboxEvent.class),
      mongo.count(Query.query(Criteria.where("replayed").is(false)),"deadLetterRecord"))
      .doOnNext(values->{pending.set(values.getT1());queue.set(values.getT2());failed.set(values.getT3());dlq.set(values.getT4());})
      .then(announcements.cleanExpired());
  }
  @Scheduled(fixedDelayString="${tacocloud.metrics.refresh-ms:5000}") public void tick() {
    if(refreshing.compareAndSet(false,true))refresh().timeout(java.time.Duration.ofSeconds(10)).doFinally(s->refreshing.set(false))
      .subscribe(v->{},e->org.slf4j.LoggerFactory.getLogger(getClass()).warn("Operational snapshot unavailable"));
  }
}
