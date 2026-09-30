package tacos.business;
import org.springframework.stereotype.Component;
import org.springframework.boot.actuate.health.*;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import reactor.core.publisher.Mono;
@Component("outbox")
public class OutboxHealth implements ReactiveHealthIndicator {
  private final ReactiveMongoTemplate mongo;
  public OutboxHealth(ReactiveMongoTemplate mongo) {this.mongo=mongo;}
  @Override public Mono<Health> health() {
    return mongo.count(Query.query(Criteria.where("state").is("FAILED")),OutboxEvent.class)
      .map(count->(count==0?Health.up():Health.down()).withDetail("failedEvents",count).build())
      .timeout(java.time.Duration.ofSeconds(3)).onErrorReturn(Health.down().withDetail("reason","OUTBOX_UNAVAILABLE").build());
  }
}
