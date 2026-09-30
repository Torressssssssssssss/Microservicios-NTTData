package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;
import tacos.*;
import tacos.web.api.ApiException;
import tacos.messaging.OrderEventType;
import java.time.Clock;
@Service
public class KitchenService {
  private final ReactiveMongoTemplate mongo;private final OutboxService outbox;private final TransactionalOperator tx;private final Clock clock;
  private final int base;private final int perItem;private final int perWaiting;
  public KitchenService(ReactiveMongoTemplate mongo,OutboxService outbox,TransactionalOperator tx,Clock clock,
      @Value("${tacocloud.kitchen.base-minutes:3}") int base,@Value("${tacocloud.kitchen.per-item-minutes:2}") int perItem,
      @Value("${tacocloud.kitchen.per-waiting-minutes:1}") int perWaiting) {
    this.mongo=mongo;this.outbox=outbox;this.tx=tx;this.clock=clock;this.base=base;this.perItem=perItem;this.perWaiting=perWaiting;
  }
  public int estimate(TacoOrder order,long waiting) {
    int quantity=order.getItems().stream().mapToInt(OrderLine::getQuantity).sum();
    int complexity=order.getItems().stream().mapToInt(line->line.getTaco().getIngredients().size()*line.getQuantity()).sum();
    return Math.toIntExact(Math.min(Integer.MAX_VALUE,base+(long)perItem*quantity+complexity/4+(long)perWaiting*waiting));
  }
  public Flux<TacoOrder> queue() {
    return mongo.find(Query.query(Criteria.where("status").is(TacoOrder.Status.CREATED)).with(Sort.by("placedAt","_id")).limit(100),TacoOrder.class);
  }
  public Mono<TacoOrder> claim(Authentication auth) {
    if(!IdentityService.role(auth,"KITCHEN")) return Mono.error(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"KITCHEN_REQUIRED"));
    Query waiting=Query.query(Criteria.where("status").is(TacoOrder.Status.CREATED));
    return tx.transactional(Mono.defer(()->{
      StationSlot slot=new StationSlot();slot.setId(auth.getName());
      return mongo.insert(slot).then(mongo.count(waiting,TacoOrder.class));
    }).flatMap(count->{
      Query fifo=Query.query(Criteria.where("status").is(TacoOrder.Status.CREATED)).with(Sort.by("placedAt","_id"));
      StatusChange history=new StatusChange(TacoOrder.Status.CREATED,TacoOrder.Status.ACCEPTED,auth.getName(),clock.instant(),"KITCHEN","CLAIM");
      return mongo.findAndModify(fifo,new Update().set("status",TacoOrder.Status.ACCEPTED).set("cookId",auth.getName())
          .set("stationId",auth.getName()).push("history",history).inc("version",1),FindAndModifyOptions.options().returnNew(true),TacoOrder.class)
        .switchIfEmpty(Mono.error(ApiException.missing("QUEUE"))).flatMap(order->{
          order.setEstimatedPrepMinutes(estimate(order,Math.max(0,count-1)));
          return mongo.updateFirst(Query.query(Criteria.where("_id").is(auth.getName())),new Update().set("orderId",order.getId()),StationSlot.class)
            .then(mongo.save(order)).flatMap(saved->outbox.append(saved,OrderEventType.STATUS_CHANGED));
        });
    })).retryWhen(reactor.util.retry.Retry.backoff(5,java.time.Duration.ofMillis(20)).maxBackoff(java.time.Duration.ofMillis(200))
      .filter(TransactionRetry::transientFailure).onRetryExhaustedThrow((spec,signal)->ApiException.conflict("CONCURRENT_CLAIM")));
  }
}
