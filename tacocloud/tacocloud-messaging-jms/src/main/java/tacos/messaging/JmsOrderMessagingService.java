package tacos.messaging;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletionStage;
import org.springframework.jms.core.JmsTemplate;
@Service @ConditionalOnProperty(name="tacocloud.messaging.transport",havingValue="jms")
public class JmsOrderMessagingService implements OrderMessagingService {
  @Value("${tacocloud.messaging.destination:tacocloud.orders}") private String destination;
  private final JmsTemplate template;
  public JmsOrderMessagingService(JmsTemplate template) { this.template=template; }
  public CompletionStage<Void> sendOrder(OrderEvent event) { java.util.concurrent.CompletableFuture<Void> result=new java.util.concurrent.CompletableFuture<>();
    try { template.convertAndSend(destination,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event),message->{message.setJMSCorrelationID(event.getCorrelationId());return message;});result.complete(null); }
    catch(Exception e) { result.completeExceptionally(e); } return result; }
}
