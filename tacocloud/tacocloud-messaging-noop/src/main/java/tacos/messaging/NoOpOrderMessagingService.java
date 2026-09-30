package tacos.messaging;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletionStage;

@Service @ConditionalOnProperty(name="tacocloud.messaging.transport",havingValue="noop")
public class NoOpOrderMessagingService implements OrderMessagingService {
  @Value("${tacocloud.messaging.destination:tacocloud.orders}") private String destination;
  
  public CompletionStage<Void> sendOrder(OrderEvent event) { return java.util.concurrent.CompletableFuture.completedFuture(null); }
}
