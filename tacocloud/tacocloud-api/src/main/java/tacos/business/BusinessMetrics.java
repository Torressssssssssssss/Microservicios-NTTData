package tacos.business;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.*;
import reactor.core.publisher.Mono;
import java.util.concurrent.TimeUnit;
import tacos.TacoOrder;
@Component
public class BusinessMetrics {
  private final MeterRegistry registry;
  public BusinessMetrics(MeterRegistry registry) { this.registry=registry; }
  public Mono<Void> afterCommit(Runnable effect) {
    return TransactionSynchronizationManager.forCurrentTransaction().doOnNext(manager->manager.registerSynchronization(new TransactionSynchronization() {
      @Override public Mono<Void> afterCommit() { return Mono.fromRunnable(effect); }
    })).then();
  }
  public Mono<Void> created(TacoOrder order,long started) {
    return afterCommit(()->{
      registry.counter("tacocloud.orders.created").increment();
      if(order.getCouponCode()!=null)registry.counter("tacocloud.coupons.applied").increment();
      registry.timer("tacocloud.orders.placement","result","success").record(System.nanoTime()-started,TimeUnit.NANOSECONDS);
    });
  }
  public void failed(Throwable error) {
    registry.counter("tacocloud.orders.failed").increment();
    if((error instanceof tacos.web.api.ApiException && ((tacos.web.api.ApiException)error).getCode().startsWith("INSUFFICIENT_STOCK")) || (error instanceof DesignValidationException && ((DesignValidationException)error).getCodes().contains("INGREDIENT_UNAVAILABLE"))) registry.counter("tacocloud.stock.rejected").increment();
  }
  public Mono<Void> transition(TacoOrder order) {
    return afterCommit(()->{
      if(order.getStatus()==TacoOrder.Status.CANCELLED)registry.counter("tacocloud.orders.cancelled").increment();
      if(order.getStatus()==TacoOrder.Status.READY)order.getHistory().stream().filter(h->h.getTo()==TacoOrder.Status.ACCEPTED).findFirst().ifPresent(start->{
        java.time.Instant end=order.getHistory().get(order.getHistory().size()-1).getAt();
        registry.timer("tacocloud.kitchen.latency").record(java.time.Duration.between(start.getAt(),end).isNegative()?java.time.Duration.ZERO:java.time.Duration.between(start.getAt(),end));
      });
    });
  }
  public void deadLetter() {registry.counter("tacocloud.events.dlq","transport","rabbit").increment();}
}
