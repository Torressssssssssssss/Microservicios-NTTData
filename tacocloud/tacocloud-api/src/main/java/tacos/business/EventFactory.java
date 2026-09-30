package tacos.business;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import tacos.*;
import tacos.messaging.*;
@Component
public class EventFactory {
  private final Clock clock;
  public EventFactory(Clock clock) { this.clock=clock; }
  public OrderEvent create(TacoOrder order,OrderEventType type) {
    OrderEvent e=new OrderEvent();e.setEventId(UUID.randomUUID().toString());e.setCorrelationId(java.util.UUID.randomUUID().toString());
    e.setOccurredAt(clock.instant().toString());e.setEventType(type);
    OrderEventPayload p=new OrderEventPayload();p.setOrderId(order.getId());p.setStatus(order.getStatus().name());
    p.setOrderVersion(order.getVersion()==null?0:order.getVersion());
    p.setItems(order.getItems().stream().map(line->{
      OrderEventPayload.Item item=new OrderEventPayload.Item();item.setName(line.getTaco().getName());item.setQuantity(line.getQuantity());
      item.setIngredients(line.getTaco().getIngredients().stream().map(Ingredient::getName).collect(Collectors.toList()));return item;
    }).collect(Collectors.toList()));e.setPayload(p);return e;
  }
}
