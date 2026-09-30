package tacos.business;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import tacos.*;
import tacos.data.OrderRepository;
import tacos.web.api.ApiException;
import tacos.messaging.OrderEventType;
@Service
public class WorkflowService {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private BusinessMetrics metrics;
  private final OrderRepository orders;private final OrderApplicationService application;private final InventoryService inventory;
  private final org.springframework.data.mongodb.core.ReactiveMongoTemplate mongo;private final OutboxService outbox;private final TransactionalOperator tx;private final Clock clock;
  public WorkflowService(OrderRepository orders,OrderApplicationService application,InventoryService inventory,OutboxService outbox,TransactionalOperator tx,Clock clock,org.springframework.data.mongodb.core.ReactiveMongoTemplate mongo) {
    this.mongo=mongo;this.orders=orders;this.application=application;this.inventory=inventory;this.outbox=outbox;this.tx=tx;this.clock=clock;
  }
  public static boolean allowed(TacoOrder.Status from,TacoOrder.Status to) {
    if(from==null) from=TacoOrder.Status.CREATED;
    switch(from) {
      case CREATED:return to==TacoOrder.Status.ACCEPTED || to==TacoOrder.Status.CANCELLED;
      case ACCEPTED:return to==TacoOrder.Status.PREPARING || to==TacoOrder.Status.CANCELLED;
      case PREPARING:return to==TacoOrder.Status.READY;
      case READY:return to==TacoOrder.Status.OUT_FOR_DELIVERY;
      case OUT_FOR_DELIVERY:return to==TacoOrder.Status.DELIVERED;
      default:return false;
    }
  }
  public void apply(TacoOrder order,TacoOrder.Status target,String actor,String source,String reason) {
    if(!allowed(order.getStatus(),target)) throw ApiException.conflict("INVALID_TRANSITION");
    order.getHistory().add(new StatusChange(order.getStatus(),target,actor,clock.instant(),source,reason));order.setStatus(target);
  }
  public Mono<TacoOrder> transition(String id,TacoOrder.Status target,Long version,String reason,Authentication auth) {
    boolean staff=IdentityService.role(auth,"KITCHEN") || IdentityService.role(auth,"ADMIN");
    if(target!=TacoOrder.Status.CANCELLED && !staff) return Mono.error(new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED"));
    Mono<TacoOrder> source=staff?orders.findById(id).switchIfEmpty(Mono.error(ApiException.missing("ORDER"))):application.owned(id,auth,false);
    return tx.transactional(source.flatMap(order->{
      if(version!=null && !Objects.equals(version,order.getVersion())) return Mono.error(ApiException.conflict("STALE_VERSION"));
      if(order.getStatus()==target) return Mono.just(order);
      apply(order,target,auth.getName(),"API",reason);
      Mono<Void> release=target==TacoOrder.Status.CANCELLED?inventory.release(id):Mono.empty();
      if(target==TacoOrder.Status.READY || target==TacoOrder.Status.CANCELLED) release=release.then(
        mongo.remove(org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("orderId").is(id)),StationSlot.class).then());
      return release.then(orders.save(order)).flatMap(saved->outbox.append(saved,target==TacoOrder.Status.CANCELLED?OrderEventType.CANCELLED:OrderEventType.STATUS_CHANGED))
        .flatMap(saved->metrics==null?Mono.just(saved):metrics.transition(saved).thenReturn(saved));
    }));
  }
  public Mono<Void> delete(String id,Authentication auth) {
    return tx.transactional(application.owned(id,auth,true).flatMap(order->{
      if(order.getStatus()!=null && order.getStatus()!=TacoOrder.Status.CREATED) return Mono.error(ApiException.conflict("ORDER_CANNOT_BE_DELETED"));
      apply(order,TacoOrder.Status.CANCELLED,auth.getName(),"API","DELETE");
      return inventory.release(id).then(orders.save(order)).flatMap(saved->outbox.append(saved,OrderEventType.CANCELLED))
          .flatMap(saved->metrics==null?Mono.just(saved):metrics.transition(saved).thenReturn(saved)).then(orders.deleteById(id));
    }));
  }
}
